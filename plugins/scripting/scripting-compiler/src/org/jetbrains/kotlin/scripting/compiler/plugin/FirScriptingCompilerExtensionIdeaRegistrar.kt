/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.services.FirScriptConfiguratorExtensionImpl
import org.jetbrains.kotlin.scripting.compiler.plugin.services.FirScriptDefinitionProviderService
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.host.ScriptingHostConfiguration

class FirScriptingCompilerExtensionIdeaRegistrar(
    // todo: move to analysis-api
    private val hostConfiguration: ScriptingHostConfiguration,
    private val scriptDefinitionSources: List<ScriptDefinitionsSource>,
    private val scriptDefinitions: List<ScriptDefinition>
) : FirExtensionRegistrar() {

    override fun ExtensionRegistrarContext.configurePlugin() {
        val definitionSources = scriptDefinitionSources
        val definitions = scriptDefinitions
        if (definitionSources.isNotEmpty() || definitions.isNotEmpty()) {
            +FirScriptDefinitionProviderService.getFactory(definitions, definitionSources)
        }

        +FirScriptConfiguratorExtensionImpl.getFactory(hostConfiguration)
    }
}