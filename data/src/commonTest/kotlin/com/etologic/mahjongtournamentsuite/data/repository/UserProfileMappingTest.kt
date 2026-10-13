package com.etologic.mahjongtournamentsuite.data.repository

import com.etologic.mahjongtournamentsuite.data.backend.dto.GlobalUserRoleDto
import com.etologic.mahjongtournamentsuite.data.backend.dto.UserProfileDto
import com.etologic.mahjongtournamentsuite.domain.model.GlobalUserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileMappingTest {
    @Test
    fun keepsTheEmaIdAndTheRolesOfTheAccount() {
        val profile = UserProfileDto(
            uid = "u1",
            email = "a@example.test",
            emaId = "12345678",
            roles = listOf(GlobalUserRoleDto.ADMIN, GlobalUserRoleDto.PLAYER),
        ).toDomain()

        assertEquals("12345678", profile.emaId)
        assertEquals(setOf(GlobalUserRole.ADMIN, GlobalUserRole.PLAYER), profile.roles)
    }

    @Test
    fun readsABlankEmaIdAsNone() {
        assertNull(UserProfileDto(uid = "u1", email = "a@example.test", emaId = " ").toDomain().emaId)
    }
}
