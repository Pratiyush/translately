package io.translately.security

/**
 * Declarative authorization marker for a JAX-RS resource method or class.
 *
 * Multiple scopes listed inside one annotation instance are **all required**
 * (intersection semantics). If you need "any of" semantics, split into
 * separate resource methods or handle it explicitly in service logic.
 *
 * Missing a required scope yields an HTTP `403` with:
 * ```json
 * { "error": { "code": "INSUFFICIENT_SCOPE", "details": { "required": ["..."] } } }
 * ```
 *
 * When applied to a class, every method on the class inherits the requirement
 * unless the method declares its own `@RequiresScope` (which overrides).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequiresScope(
    vararg val value: Scope,
)
