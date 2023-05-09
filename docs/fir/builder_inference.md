## FIR/Builder inference
See also: [Kotlin Spec: Builder-style type inference](https://kotlinlang.org/spec/type-inference.html#builder-style-type-inference)

### Glossary
#### CS = Constraint system
An instance of `org.jetbrains.kotlin.resolve.calls.inference.model.NewConstraintSystemImpl`
#### Call-tree
A tree of calls, in which constraint systems are joined and solved(completed) together
#### Postponed type variable
Type-variable, that was used to build a stub type on, and fixation of which is postponed until the end of lambda analysis.
Selection of type variables to postpone happens during [Initiation](#initiation) phase
#### Stub type
Type, that is equal to anything from the perspective of subtyping.
Carries information about its base postponed type variable

Such types are used during [analysis of lambda body](#lambda-body-analysis) inplace of postponed type variables to resolve calls,
after end of analysis occurrences of such types are replaced with corresponding type inference result for its base type variable

Ex:
```kotlin
buildList { /* this: MutableList<Stub(T)> -> */
    val self/*: MutableList<Stub(T)> */ = this
    self.add("") // String <: Stub(T) from argument
}
```
#### Proper constraint
A constraint that doesn't reference any type variables
### Builder inference algorithm
The algorithm consists of the following phases, all of which happen during constraint system completion
- [Initiation](#initiation) - deciding if lambda is suitable for builder inference
- [Lambda analysis preparation](#lambda-analysis-preparation) - stub type and builder inference session creating
- [Lambda body analysis](#lambda-body-analysis) - collecting calls inside lambda body, which references stub-types among input types
- [Lambda analysis finalization](#lambda-analysis-finalization) - integrating collected information
    - [Result write](#result-write) - writing of builder inference result to FIR tree of lambda (and implicit receiver stack update)
    - [Result backpropagation](#result-backpropagation) - propagation of builder inference result to CS of call-tree

### Detailed description
#### Initiation
Before, we try to fix all type variables that have enough type info to be fixed and analyze all lambda arguments, 
whose input types depend on such type variables. 

If there are still not analyzed lambda arguments left, we try to perform builder inference

Entrypoint to builder inference is the function `org.jetbrains.kotlin.fir.resolve.inference.ConstraintSystemCompleter.tryToCompleteWithBuilderInference`

*It happens only during full completion*

Then, all lambda arguments, that weren't analyzed and satisfy the following criteria are considered:

- lambda argument has a non-empty list of input types
- any of the input types contain a non-fixed type variable in arguments (If the input type is a type-variable itself, it is **NOT** considered)

Such type-variables are marked as postponed and analysis of the lambda argument is performed

#### Lambda analysis preparation
First, `org.jetbrains.kotlin.fir.resolve.inference.PostponedArgumentsAnalyzer.analyzeLambda`
- creates stub types for all postponed type variables
- substitutes type-variable-type -> stub-type in a receiver, context receiver, parameters, and return types

Then, before the actual transforming of the lambda body, an instance of `org.jetbrains.kotlin.fir.resolve.inference.FirBuilderInferenceSession`
is created and set as the current inference session

#### Lambda body analysis
During body analysis of a lambda, for each call (inside lambda body) that is completed in the FULL mode, we ask the inference session if it can
be completed

See `org.jetbrains.kotlin.fir.resolve.inference.FirBuilderInferenceSession.shouldRunCompletion`
It is needed in cases, where the result of builder inference can help with further system completion

Example:
```kotlin
fun <M> materialize(): M = null!!
fun foo() = buildList {
    addAll(materialize()) // (1) To be able to infer type for materialize, we need to know builder inference result
    add("str") // (2) This call already has complete type info, so we allow it to complete
}
```

In situation 1, we mark the call as partially completed, see `org.jetbrains.kotlin.fir.resolve.inference.FirBuilderInferenceSession.addPartiallyResolvedCall`
In situation 2, we mark the call as a completed aka common call, see `org.jetbrains.kotlin.fir.resolve.inference.FirInferenceSession.addCompletedCall`

Note: Calls that were analyzed in PARTIAL mode will not be considered, as it always analyzed during FULL completion of the outer call
Such calls will not be added to partially/common calls

The criteria to mark call as a partially completed is following:
1. There's no contradiction in the constraint system of the call
2. The candidate (call) is suitable for builder inference based on **ANY** of the following conditions:
    - The dispatch receiver's type contains a stub type
    - The extension receiver's type contains a stub type and the corresponding symbol has a builder inference annotation in the session
3. The candidate doesn't have any not analyzed postponed atoms
4. Any type variable in call CS doesn't have a proper constraint, and it's not a postponed variable

#### Lambda analysis finalization
See `org.jetbrains.kotlin.fir.resolve.inference.PostponedArgumentsAnalyzer.applyResultsOfAnalyzedLambdaToCandidateSystem`

Once the lambda body was analyzed return arguments are added into call-tree
Then, we perform inference of postponed type variables

See `org.jetbrains.kotlin.fir.resolve.inference.FirBuilderInferenceSession.inferPostponedVariables`

After such steps, the constraint system of the call-tree contains all output-type constraints
While systems for common calls and partially completed calls inside the lambda body contain all internal type constraints in the form of
constraints with stub types

See `org.jetbrains.kotlin.fir.resolve.inference.FirBuilderInferenceSession.buildCommonSystem`

To find a solution for postponed type variables we need to integrate all aforementioned constraints
We do so, by constructing a new constraint system in the following way:
- Register all not-fixed type variables from CS of call-tree
- Re-apply all initial constraints from CS of call-tree
    - Substitute Stub(PostponedTV) with TypeVariableType(PostponedTV)
    - Only constraints between two non-proper types are applied
    - Constraints, that originate from other builder inference results are skipped
- Re-apply all initial constraints of all common calls that were collected during lambda body analysis
    - Using the same algorithm as above
- Re-apply all initial constraints of all partially completed calls
    - Also, register all fixed type variables and bring its fixation result as an equality constraint

Note: Such a system shouldn't contain stub types in constraints anymore (but only for postponed type variables, that were selected during initiation)

If the system is empty, we bail out and skip to the result write

After such a constraint system is constructed, we call the constraint system completer to solve the system

Note: As partially completed calls couldn't contain postponed arguments in the call-tree, we don't analyze any postponed arguments here

#### Result write
See `org.jetbrains.kotlin.fir.resolve.inference.FirBuilderInferenceSession.updateCalls`

**Resulting substitutor:**
First, Stub(PostponedTV) -> TypeVariableType(PostponedTV) -> Solution(PostponedTV)
Then, if Solution, Stub(*) || TypeVariableType(*) -> ERROR

Note: Effectively, it means that if we have an inference result for a postponed type variable, we replace stub/type variable types inside with
an error types

Unclear: If the constraint system wasn't empty we apply the resulting substitutor to fixed type variables from CS of call-tree

We apply the resulting substitutor to all type references inside of lambda, recursively

After that, we apply the same substitution to all the implicit receivers, which originated from lambdas. It is needed to have full type information
during the analysis of lambda arguments inside of return arguments in the current lambda
(since those arguments may be analyzed after builder inference completion)

Then, we call the completion results writer to store completion results for partially resolved calls with the result substitutor

#### Result backpropagation
See `org.jetbrains.kotlin.fir.resolve.inference.PostponedArgumentsAnalyzer.applyResultsOfAnalyzedLambdaToCandidateSystem`

Once, the result for postponed variables is inferred and stored, we need to propagate results to the CS of call-tree
since its completion isn't finished yet

To do so, we bring inject subtype constraint with result type from postponed variable inference

After that, we unmark type variables as postponed

Lambda analysis finished, and we return to the Initiation

### Potential problems
#### Unclear naming
In fact, builder inference doesn't only apply to "builders", but a general algorithm that applies to lambda arguments of particular shape
#### Incomplete criteria to perform builder-inference
Criteria to perform builder inference doesn't consider lambdas, which input types contain only type-variable-types
#### Incomplete criteria to mark calls as partially completed during lambda analysis
- Criteria to mark calls as partially completed perform depends on builder inference annotation
- In case of present postponed arguments somewhere in the call-tree it will change behavior, failing to infer types
- Ad-hoc check for the presence of proper constraints
#### Lost diagnostics
The constraint system that is used to infer results for postponed type variables isn't checked for inconsistencies
#### Unclear substitution of fixed type variables
During result writing, we update fixed type variables of the main call-tree, reasons for that aren't clear