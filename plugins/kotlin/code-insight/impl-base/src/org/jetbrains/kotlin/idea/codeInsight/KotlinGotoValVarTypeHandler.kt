// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

class KotlinGotoValVarTypeHandler: GotoDeclarationHandlerBase() {
    override fun getGotoDeclarationTarget(
        sourceElement: PsiElement?,
        editor: Editor?
    ): PsiElement? {
        val elementType = sourceElement.elementType
        if ((elementType == KtTokens.VAL_KEYWORD || elementType == KtTokens.VAR_KEYWORD)) {
            val property = sourceElement?.parent as? KtProperty ?: return null
            return analyze(property) {
                val type = property.getReturnKtType()
                val psi = when(type) {
                    is KtTypeParameterType -> type.symbol.psi
                    is KtNonErrorClassType -> type.classSymbol.psi
                    else -> null
                }
                psi
            }
        }
        return null
    }
}