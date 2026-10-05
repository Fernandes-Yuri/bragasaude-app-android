package br.com.bragasaude.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.com.bragasaude.R

/** Cada cena ocupa uma célula independente da arte, disponível sem internet. */
enum class TourEducationScene(val cell: Int, val description: String) {
    SELF_CARE(0, "Personagem registra a pressão em casa com um aparelho de braço."),
    NUTRITION(1, "Personagem faz uma refeição com alimentos variados."),
    MOVEMENT(2, "Personagem caminha ao ar livre com sua garrafa de água."),
    FAMILY(3, "Titular e familiar conversam pelos seus celulares."),
    CAREGIVER(4, "Titular e cuidadora escolhem juntos o acompanhamento autorizado."),
    METRICS(5, "Cuidadora consulta os registros de saúde no celular."),
    MESSAGES(6, "Cuidadora escreve uma mensagem de incentivo."),
    SHOPPING(7, "Cuidadora consulta a lista de compras com uma sacola de alimentos."),
    ALERTS(8, "Cuidadora vê um aviso discreto e conversa com a titular.")
}

@Composable
fun TourEducationIllustration(scene: TourEducationScene, modifier: Modifier = Modifier) {
    val artwork = ImageBitmap.imageResource(R.drawable.tour_onboarding_story)
    val painter = remember(artwork, scene) {
        val column = scene.cell % 3
        val row = scene.cell / 3
        val left = artwork.width * column / 3
        // A arte tem alturas diferentes entre as fileiras; o recorte respeita os espaços brancos reais.
        val rowEdges = intArrayOf(0, 340, 644, 1000)
        val top = artwork.height * rowEdges[row] / 1000
        val bottom = artwork.height * rowEdges[row + 1] / 1000
        BitmapPainter(
            image = artwork,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(
                artwork.width * (column + 1) / 3 - left,
                bottom - top
            )
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth().height(250.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White
    ) {
        Image(
            painter = painter,
            contentDescription = scene.description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
