/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.load.java.structure.impl

import com.intellij.psi.PsiRecordComponent
import org.jetbrains.kotlin.load.java.structure.JavaRecordComponent
import org.jetbrains.kotlin.load.java.structure.JavaType
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementPsiSource

class JavaRecordComponentImpl(
    psi: JavaElementPsiSource<PsiRecordComponent>
) : JavaMemberImpl<PsiRecordComponent>(psi), JavaRecordComponent {
    override val type: JavaType
        get() = JavaTypeImpl.create(sourceFactory.createTypeSource(psi.type))

    override val isVararg: Boolean
        get() = psi.isVarArgs
}
