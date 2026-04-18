package io.translately.api.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

/**
 * Registers `jackson-module-kotlin` with the Quarkus-managed [ObjectMapper]
 * so Kotlin `data class` request DTOs (e.g. the nested DTOs in
 * `AuthResource`) can be deserialized without requiring hand-written
 * `@JsonCreator` annotations on every constructor.
 *
 * `ObjectMapperCustomizer` is Quarkus's official extension point for
 * tweaking the singleton [ObjectMapper] that `quarkus-rest-jackson` uses on
 * the request/response path. Any CDI bean implementing this interface is
 * discovered and invoked during startup.
 *
 * Keeping the customizer in `:backend:api` (alongside the resources that
 * consume it) scopes the Kotlin module to the REST surface; service-layer
 * code that works with Jackson directly would register the module
 * independently.
 */
@Singleton
class KotlinObjectMapperCustomizer : ObjectMapperCustomizer {
    override fun customize(objectMapper: ObjectMapper) {
        objectMapper.registerModule(KotlinModule.Builder().build())
    }
}
