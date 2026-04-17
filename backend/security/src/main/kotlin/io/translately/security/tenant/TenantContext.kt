package io.translately.security.tenant

import jakarta.enterprise.context.RequestScoped

/**
 * Per-request holder for the current tenant identifier.
 *
 * The value is the **URL-path organization identifier** exactly as the client
 * sent it — either a ULID or a slug. Service code resolves it to an internal
 * bigserial `organization_id` when it first needs to touch the database, and
 * enables the Hibernate multi-tenant filter for the rest of the request.
 *
 * A null value means "no tenant in this request's URL path"; not every
 * endpoint is tenant-scoped (login, signup, `/q/health`, `GET /`).
 *
 * Set by `io.translately.api.tenant.TenantRequestFilter`, read by service
 * code and by the Hibernate filter activator.
 */
@RequestScoped
open class TenantContext {
    private var identifier: String? = null

    /** Returns the URL-path organization identifier, or null if this isn't a tenant-scoped request. */
    open fun current(): String? = identifier

    /** Set or clear the identifier. The filter calls this exactly once per request. */
    open fun set(identifier: String?) {
        this.identifier = identifier?.takeIf { it.isNotBlank() }
    }

    /** True iff a tenant is currently bound to this request. */
    open fun isBound(): Boolean = identifier != null
}
