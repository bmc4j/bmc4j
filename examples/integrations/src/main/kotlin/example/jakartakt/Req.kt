package example.jakartakt

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/**
 * A KOTLIN DTO whose Jakarta constraints become proof preconditions WITHOUT a hand-written Java
 * mirror class. The `bmc-constraints-jakarta` **KSP** SymbolProcessor reads these annotations off the
 * Kotlin declarations and generates `ReqConstraints.assumeValid(Req)` — the same helper the javac
 * processor generates for a Java DTO (see [example.jakarta.User]).
 *
 * Use-site target note: validation libraries resolve `@field:`-targeted constraints (the backing
 * field is what they read), so that is the recommended form on a Kotlin property. The processor also
 * accepts `@param:` (constructor parameter) and bare annotations.
 */
data class Req(
        // A non-nullable Kotlin `Int` -> a JVM primitive field, so the bound is un-guarded (`>= 1`).
        @field:Min(1) val qty: Int,
        @field:PositiveOrZero val cents: Int,
        // A boxed `Int?` without @NotNull -> null is VALID; the generated bound is null-guarded.
        @field:Max(120) val ageOrNull: Int?,
        // A nullable String with @NotNull + @Size: non-null and length in [3, 20].
        @field:NotNull @field:Size(min = 3, max = 20) val name: String?,
)
