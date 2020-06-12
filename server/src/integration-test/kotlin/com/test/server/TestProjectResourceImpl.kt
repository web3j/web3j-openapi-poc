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
package com.test.server

import com.test.server.humanstandardtoken.HumanStandardTokenLifecycleImpl
import com.test.core.TestProjectResource
import org.glassfish.jersey.server.ExtendedUriInfo
import org.web3j.openapi.server.ContractResourceImpl
import org.web3j.protocol.Web3j
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.ContractGasProvider
import javax.annotation.processing.Generated

@Generated
class TestProjectResourceImpl(
    web3j: Web3j,
    transactionManager: TransactionManager,
    defaultGasProvider: ContractGasProvider,
    uriInfo: ExtendedUriInfo
) : TestProjectResource, ContractResourceImpl(uriInfo) {

    override val humanStandardToken =
        HumanStandardTokenLifecycleImpl(
            web3j,
            transactionManager,
            defaultGasProvider
        )
}