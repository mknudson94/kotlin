package org.jetbrains.kotlin.analysis.api.impl.base.java.source

import com.intellij.psi.*
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementTypeSource

internal abstract class JavaElementDelegatingTypeSourceWithSmartPointer<PSI : PsiElement, TYPE : PsiType> : JavaElementTypeSource<TYPE>() {
    abstract val psiPointer: SmartPsiElementPointer<out PSI>
    abstract fun getType(psi: PSI): TYPE

    override val type: TYPE
        get() {
            val psi = psiPointer.element
                ?: error("Cannot restore $psiPointer")
            return getType(psi)
        }
}

internal class JavaElementDelegatingVariableReturnTypeSourceWithSmartPointer<TYPE : PsiType>(
    override val psiPointer: SmartPsiElementPointer<out PsiVariable>,
    override val factory: JavaElementSourceFactory,
) : JavaElementDelegatingTypeSourceWithSmartPointer<PsiVariable, TYPE>() {

    override fun getType(psi: PsiVariable): TYPE {
        @Suppress("UNCHECKED_CAST")
        return psi.type as TYPE
    }
}

internal class JavaElementDelegatingMethodReturnTypeSourceWithSmartPointer<TYPE : PsiType>(
    override val psiPointer: SmartPsiElementPointer<out PsiMethod>,
    override val factory: JavaElementSourceFactory,
) : JavaElementDelegatingTypeSourceWithSmartPointer<PsiMethod, TYPE>() {

    override fun getType(psi: PsiMethod): TYPE {
        @Suppress("UNCHECKED_CAST")
        return psi.returnType as TYPE
    }
}
internal class JavaElementDelegatingExpressionTypeSourceWithSmartPointer<TYPE : PsiType>(
    override val psiPointer: SmartPsiElementPointer<out PsiExpression>,
    override val factory: JavaElementSourceFactory,
) : JavaElementDelegatingTypeSourceWithSmartPointer<PsiExpression, TYPE>() {

    override fun getType(psi: PsiExpression): TYPE {
        @Suppress("UNCHECKED_CAST")
        return psi.type as TYPE
    }
}
