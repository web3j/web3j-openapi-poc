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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.web3j.openapi.codegen.common.EventResource
import org.web3j.openapi.codegen.common.Import
import org.web3j.openapi.codegen.utils.getStructCallParameters
import org.web3j.openapi.codegen.utils.CopyUtils
import org.web3j.openapi.codegen.utils.GeneratorUtils.argumentName
import org.web3j.openapi.codegen.utils.GeneratorUtils.sanitizedName
import org.web3j.openapi.codegen.utils.TemplateUtils
import org.web3j.openapi.codegen.utils.getReturnType
import org.web3j.openapi.codegen.utils.isTransactional
import org.web3j.protocol.core.methods.response.AbiDefinition
import java.io.File
import java.nio.file.Paths

internal class ResourcesImplGenerator(
    val packageName: String,
    private val contractName: String,
    private val resourcesDefinition: List<AbiDefinition>,
    private val folderPath: String
) {
    fun generate() {
        generateClass().writeTo(File(folderPath))
        File(folderPath)
            .walkTopDown()
            .filter { file -> file.name.endsWith(".kt") }
            .forEach { file ->
                CopyUtils.kotlinFormat(file)
            }
        copySources()
    }

    private fun generateClass(): FileSpec {
        val resourcesFile = FileSpec.builder(
            "$packageName.server.${contractName.toLowerCase()}",
            "${contractName.capitalize()}ResourceImpl"
        )

        val contractClass = ClassName(
            packageName,
            contractName.capitalize()
        )
        val extendedUriInfoClass = ClassName(
            "org.glassfish.jersey.server",
            "ExtendedUriInfo"
        )

        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter(
                contractName.decapitalize(),
                contractClass
            ).addParameter(
                "uriInfo",
                extendedUriInfoClass
            )

        val contractResourceClass = ClassName(
            "$packageName.core.${contractName.toLowerCase()}",
            "${contractName.capitalize()}Resource"
        )

        val eventsResourcesClass = ClassName(
            "$packageName.core.${contractName.toLowerCase()}",
            "${contractName.capitalize()}Events"
        )

        val resourcesClass = TypeSpec
            .classBuilder("${contractName.capitalize()}ResourceImpl")
            .primaryConstructor(constructorBuilder.build())
            .addProperty(
                PropertySpec.builder(
                    contractName.decapitalize(),
                    contractClass,
                    KModifier.PRIVATE
                )
                    .initializer(contractName.decapitalize())
                    .build()
            ).addProperty(
                PropertySpec.builder(
                    "uriInfo",
                    extendedUriInfoClass,
                    KModifier.PRIVATE
                )
                    .initializer("uriInfo")
                    .build()
            ).addProperty(
                PropertySpec.builder(
                    "events",
                    eventsResourcesClass,
                    KModifier.OVERRIDE
                )
                    .initializer("${contractName.capitalize()}EventsImpl(${contractName.decapitalize()}, uriInfo)")
                    .build()
            ).addSuperinterface(contractResourceClass)

        resourcesClass.addFunctions(generateFunctions())

        return resourcesFile
            .addType(resourcesClass.build())
            .build()
    }

    private fun generateFunctions(): List<FunSpec> {
        val functions = mutableListOf<FunSpec>()
        resourcesDefinition
            .filter { it.type == "function" }
            .forEach {
                val sanitizedAbiDefinitionName = it.sanitizedName()
                if (!it.isTransactional() && it.outputs.isEmpty()) return@forEach
                val returnType = it.getReturnType(packageName, contractName.toLowerCase())
                val funSpec = FunSpec.builder(sanitizedAbiDefinitionName)
                    .returns(returnType)
                    .addModifiers(KModifier.OVERRIDE)
                val code = if (it.inputs.isEmpty()) {
                    "${contractName.decapitalize()}.${it.sanitizedName(true)}().send()"
                } else {
                    val nameClass = ClassName(
                        "$packageName.core.${contractName.toLowerCase()}.model",
                        "${sanitizedAbiDefinitionName.capitalize()}Parameters"
                    )
                    funSpec.addParameter(
                        "${sanitizedAbiDefinitionName.decapitalize()}Parameters",
                        nameClass
                    )
                    """
                        ${contractName.decapitalize()}.${getFunctionName(it.sanitizedName(true))}(
                                ${getCallParameters(it.inputs, sanitizedAbiDefinitionName)}
                            ).send()
                    """.trimIndent()
                }

                funSpec.addCode(wrapCode(code, returnType.toString()))
                functions.add(funSpec.build())
            }
        return functions
    }

    private fun wrapCode(code: String, returnType: String): String {
        return if (returnType.startsWith("org.web3j.openapi.core.models.TransactionReceiptModel"))
            "return TransactionReceiptModel($code)"
        else {
            val innerType = returnType.substringAfter("<").removeSuffix(">")
            when {
                innerType.startsWith("org.web3j.tuples") -> wrapTuplesCode(code, innerType)
                innerType.contains("StructModel") -> "return org.web3j.openapi.core.models.ResultModel($code.toModel())"
                else -> "return org.web3j.openapi.core.models.ResultModel($code)"
            }
        }
    }

    private fun wrapTuplesCode(code: String, returnType: String): String {
        val components = returnType.substringAfter("<")
            .removeSuffix(">")
            .split(",")

        val variableNames = components.mapIndexed { index, component ->
            if (component.endsWith("StructModel")) "${component.removeSuffix("StructModel").substringAfterLast(".").decapitalize()}$index"
            else "${component.substringBefore("<").substringAfterLast(".").decapitalize()}$index"
        }.joinToString(",")

        val tupleConstructor = components.mapIndexed { index, component ->
            if (component.endsWith("StructModel")) "${component.removeSuffix("StructModel").substringAfterLast(".").decapitalize()}$index.toModel()"
            else "${component.substringBefore("<").substringAfterLast(".").decapitalize()}$index"
        }.joinToString(",")

        return """val ($variableNames) = $code
                return org.web3j.openapi.core.models.ResultModel(
                    Tuple${components.size}($tupleConstructor)
                )""".trimMargin()
    }

    private fun getFunctionName(name: String): String {
        return if (name == "short" || name == "long" || name == "double" || name == "float" || name == "char") "_$name"
        else name
    }

    private fun getCallParameters(inputs: MutableList<AbiDefinition.NamedType>, functionName: String): String {
        var callParameters = ""
        inputs.forEachIndexed { index, input ->
            callParameters +=
                if (input.type == "tuple")
                    "${getStructCallParameters(contractName, input, functionName, "${functionName.decapitalize()}Parameters.${argumentName(input.name, index)}")},"
                else
                    "${functionName.decapitalize()}Parameters.${argumentName(input.name, index)},"
        }
        return callParameters.removeSuffix(",")
    }

    private fun eventImports(): List<Import> {
        return resourcesDefinition
            .filter { it.type == "event" }
            .map { abiDefinition ->
                Import(
                    "import $packageName.server.${contractName.toLowerCase()}.events.${abiDefinition.name.capitalize()}EventResourceImpl"
                )
            }
    }

    private fun eventsResources(): List<EventResource> {
        return resourcesDefinition
            .filter { it.type == "event" }
            .map { abiDefinition ->
                EventResource(
                    capitalizedName = abiDefinition.sanitizedName().capitalize()
                )
            }
    }

    private fun copySources() {
        val context = mutableMapOf<String, Any>()

        context["packageName"] = packageName
        context["lowerCaseContractName"] = contractName.toLowerCase()
        context["decapitalizedContractName"] = contractName.decapitalize()
        context["capitalizedContractName"] = contractName.capitalize()
        context["EventResource"] = eventsResources()
        context["eventImports"] = eventImports()

        val contractFolder = File(
            Paths.get(
                folderPath,
                packageName.replace(".", "/"),
                "server",
                contractName.toLowerCase()
            ).toString()
        ).apply {
            mkdirs()
        }

        TemplateUtils.generateFromTemplate(
            context = context,
            outputDir = contractFolder.canonicalPath,
            template = TemplateUtils.mustacheTemplate("server/src/contractImpl/EventsResourceImpl.mustache"),
            name = "${contractName.capitalize()}EventsImpl.kt"
        )
    }
}
