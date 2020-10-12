/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package com.icerockdev.library

import dev.icerock.moko.errors.MR
import dev.icerock.moko.errors.handler.ExceptionHandler
import dev.icerock.moko.errors.mappers.ExceptionMappersStorage
import dev.icerock.moko.errors.presenters.AlertErrorPresenter
import dev.icerock.moko.mvvm.livedata.LiveData
import dev.icerock.moko.mvvm.livedata.MutableLiveData
import dev.icerock.moko.mvvm.livedata.readOnly
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import dev.icerock.moko.network.LanguageProvider
import dev.icerock.moko.network.features.ETagCacheFeature
import dev.icerock.moko.network.features.LanguageFeature
import dev.icerock.moko.network.generated.apis.PetApi
import dev.icerock.moko.resources.desc.desc
import io.ktor.client.HttpClient
import io.ktor.client.features.cache.HttpCache
import io.ktor.client.features.cache.storage.HttpCacheStorage
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.get
import io.ktor.http.takeFrom
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class TestViewModel : ViewModel() {

    val exceptionHandler = ExceptionHandler(
        errorPresenter = AlertErrorPresenter(
            // temporary fix https://youtrack.jetbrains.com/issue/KT-41823
            alertTitle = MR.strings.moko_errors_presenters_alertDialogTitle.desc(),
            positiveButtonText = MR.strings.moko_errors_presenters_alertPositiveButton.desc()
        ),
        exceptionMapper = {
            it.toString().desc()
        }
    )

    private val httpClient = HttpClient {
        install(HttpCache)
//        install(LanguageFeature) {
//            languageHeaderName = "X-Language"
//            languageCodeProvider = LanguageProvider()
//        }
//        install(Logging) {
//            level = LogLevel.INFO
//            logger = object : Logger {
//                override fun log(message: String) {
//                    println(message)
//                }
//            }
//        }
    }
    private val petApi = PetApi(
        basePath = "https://petstore.swagger.io/v2/",
        httpClient = httpClient,
        json = Json {
            ignoreUnknownKeys = true
        }
    )

    private val _petInfo = MutableLiveData<String?>(null)
    val petInfo: LiveData<String?> = _petInfo.readOnly()

    init {
        reloadPet()
    }

    fun onRefreshPressed() {
        reloadPet()
    }

    private fun reloadPet() {
        viewModelScope.launch {
            exceptionHandler.handle {
//                val pet = petApi.findPetsByStatus(listOf("available"))
                val pet = httpClient.get<String> {
                    url {
                        takeFrom("https://www.thepolyglotdeveloper.com/css/custom.min.css")
                    }
                }
                _petInfo.value = pet.toString()
            }.execute()
        }
    }
}
