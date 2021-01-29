/*
 * Copyright 2020 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.openapi.codegen.servergen.subgenerators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import org.web3j.openapi.codegen.utils.CopyUtils
import org.web3j.openapi.codegen.utils.extractStructs
import org.web3j.openapi.codegen.utils.structName
import org.web3j.protocol.core.methods.response.AbiDefinition
import java.io.File

class StructExtensionsGenerator(
    val packageName: String,
    private val contractName: String,
    private val resourcesDefinition: List<AbiDefinition>,
    private val folderPath: String
) {

    fun generate() {
        val structs = extractStructs(resourcesDefinition)
        if (!structs.isNullOrEmpty())generateExtensions(structs).writeTo(File(folderPath))
        File(folderPath)
            .walkTopDown()
            .filter { file -> file.name.endsWith(".kt") }
            .forEach { file ->
                CopyUtils.kotlinFormat(file)
            }
    }

    private fun generateExtensions(structs: List<AbiDefinition.NamedType?>): FileSpec {
        val extensionsFile = FileSpec.builder(
            "$packageName.server.${contractName.toLowerCase()}",
            "${contractName.capitalize()}Extensions"
        )

        structs.forEach { structDefinition ->
            val structName = structDefinition!!.internalType.structName

            val contractClass = ClassName(
                "$packageName.${contractName.capitalize()}",
                structName
            )

            val modelClass = ClassName(
                "$packageName.core.${contractName.toLowerCase()}.model",
                "${structName}StructModel"
            )

            val code = "return ${modelClass.simpleName}(${extensionDefinitionParameters(structDefinition)})"

            val extensionFunction = FunSpec.builder("toModel")
                .receiver(contractClass)
                .returns(modelClass)
                .addCode(code)
                .build()
            extensionsFile.addFunction(extensionFunction)
        }

        return extensionsFile
            .build()
    }

    private fun extensionDefinitionParameters(structDefinition: AbiDefinition.NamedType): String {
        return structDefinition.components.joinToString(",") { structField ->
            if (structField.components.isNullOrEmpty())
                structField.name
            else
                "${structField.name}.toModel()"
        }
    }
}
