package com.puroaudio.player.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.puroaudio.player.R

@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.artwork_placeholder),
        error = painterResource(R.drawable.artwork_placeholder),
        fallback = painterResource(R.drawable.artwork_placeholder),
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
    )
}
