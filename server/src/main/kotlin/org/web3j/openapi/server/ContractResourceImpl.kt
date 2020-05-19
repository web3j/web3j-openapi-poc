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
package org.web3j.openapi.server

import org.glassfish.jersey.server.ExtendedUriInfo
import org.glassfish.jersey.server.internal.routing.UriRoutingContext
import org.glassfish.jersey.server.model.Resource
import org.web3j.openapi.core.ContractResource

abstract class ContractResourceImpl(
    private val uriInfo: ExtendedUriInfo
) : ContractResource {

    override fun findAll(): List<String> {
        val resourceClass = (uriInfo as UriRoutingContext).resourceClass
        return Resource.builder(resourceClass).build().childResources.map { it.path }
    }
}
