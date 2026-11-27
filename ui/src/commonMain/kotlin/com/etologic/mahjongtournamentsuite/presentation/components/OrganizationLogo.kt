package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.etologic.mahjongtournamentsuite.domain.model.DEFAULT_ORGANIZATION_COLOR
import com.etologic.mahjongtournamentsuite.domain.model.Organization
import com.etologic.mahjongtournamentsuite.domain.model.TournamentOrganizer
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.logo_ema
import org.jetbrains.compose.resources.painterResource

/**
 * The logo of a club or a federation, in a circle. Without a logo the circle has the color of the organization and the
 * first letter of its name, in white.
 */
@Composable
fun OrganizationLogo(
    name: String,
    logoUrl: String?,
    primaryColor: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    if (logoUrl.isNullOrBlank()) {
        Box(
            modifier = modifier.size(size).clip(shape).background(organizationColor(primaryColor)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    } else {
        AsyncImage(
            model = logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(shape).background(Color.White),
        )
    }
}

@Composable
fun OrganizationLogo(organization: Organization?, size: Dp = 40.dp, modifier: Modifier = Modifier) = OrganizationLogo(
    name = organization?.name.orEmpty(),
    logoUrl = organization?.logoUrl,
    primaryColor = organization?.primaryColor ?: DEFAULT_ORGANIZATION_COLOR,
    size = size,
    modifier = modifier,
)

@Composable
fun OrganizationLogo(organizer: TournamentOrganizer, size: Dp = 40.dp, modifier: Modifier = Modifier) = OrganizationLogo(
    name = organizer.name,
    logoUrl = organizer.logoUrl,
    primaryColor = organizer.primaryColor,
    size = size,
    modifier = modifier,
)

/** The color of an organization from its "#RRGGBB" text. A wrong text gives the default green. */
fun organizationColor(hex: String): Color {
    val normalized = hex.trim().uppercase().takeIf { Regex("^#[0-9A-F]{6}$").matches(it) } ?: DEFAULT_ORGANIZATION_COLOR
    val value = normalized.substring(1).toLong(16)
    return Color(0xFF000000 or value)
}

/**
 * The logo of the European Mahjong Association, shown on the screens that the venue projects. Its white is
 * transparent in the image, so it sits on a white circle: on a dark page it would vanish.
 */
@Composable
fun EmaLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.logo_ema),
        contentDescription = "European Mahjong Association",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).clip(CircleShape).background(Color.White),
    )
}
