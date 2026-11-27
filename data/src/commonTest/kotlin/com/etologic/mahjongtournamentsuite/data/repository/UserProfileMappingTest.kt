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
            roles = listOf(GlobalUserRoleDto.SUPER_ADMIN, GlobalUserRoleDto.PLAYER),
        ).toDomain()

        assertEquals("12345678", profile.emaId)
        assertEquals(setOf(GlobalUserRole.SUPER_ADMIN, GlobalUserRole.PLAYER), profile.roles)
    }

    @Test
    fun readsABlankEmaIdAsNone() {
        assertNull(UserProfileDto(uid = "u1", email = "a@example.test", emaId = " ").toDomain().emaId)
    }

    @Test
    fun keepsAPhotoAndReadsABlankOneOrNoneAsNone() {
        assertEquals("https://photos.test/a.png", UserProfileDto(uid = "u1", email = "a@example.test", photoUrl = "https://photos.test/a.png").toDomain().photoUrl)
        assertNull(UserProfileDto(uid = "u1", email = "a@example.test", photoUrl = " ").toDomain().photoUrl)
        assertNull(UserProfileDto(uid = "u1", email = "a@example.test").toDomain().photoUrl)
        assertNull(UserProfileDto(uid = "u1", email = "a@example.test").toDomain().emaId)
    }
}
