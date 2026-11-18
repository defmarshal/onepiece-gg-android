package gg.opgg.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A full-screen "there is nothing here" state.
 *
 * These used to be one grey line of centred text, produced by the same
 * `ErrorBox` that reported real failures. That made three different situations -
 * a dead-end search, a genuinely empty account, and a network error - look
 * identical, and said nothing about what to do next.
 *
 * An empty state earns its space only if it answers two questions: *what is
 * missing*, and *what now*. So [title] says the first, and [action] is where the
 * second lives whenever there is genuinely something to offer. Copy that cannot
 * be acted on should not get a button.
 *
 * This lives in its own file rather than in `AppRoot.kt` because it is used from
 * there, from `BuilderScreen.kt` and from `MetaScreen.kt`.
 */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(AppColors.SurfaceRaised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AppColors.IconInactive,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            fontSize = AppType.title,
            fontWeight = FontWeight.Bold,
            color = AppColors.OnSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                fontSize = AppType.meta,
                color = AppColors.TextDim,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

/**
 * A full-screen failure, kept visually distinct from [EmptyState].
 *
 * The tint is the point: a red icon says "something went wrong, this is not
 * simply empty", which a grey line never did. [body] is where a retry hint goes;
 * the app bar already carries the actual retry control on most screens, so this
 * does not invent a second one.
 */
@Composable
internal fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(AppColors.SurfaceRaised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = AppColors.Danger,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            fontSize = AppType.title,
            fontWeight = FontWeight.Bold,
            color = AppColors.Danger,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                fontSize = AppType.meta,
                color = AppColors.TextDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
