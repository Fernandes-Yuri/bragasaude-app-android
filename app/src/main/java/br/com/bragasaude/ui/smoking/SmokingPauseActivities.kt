package br.com.bragasaude.ui.smoking

/** Optional local ideas adapted from WHO Quick tips, checked on 22/10/2026.
 * Four prompts organize screen time; they are not a clinical exercise protocol.
 */
internal enum class SmokingPauseActivity(
    val title: String,
    val description: String,
    val guidance: String,
    val steps: List<String>
) {
    NOTICE(
        "Observar ao redor",
        "Uma ideia que você pode experimentar sentado.",
        "Sem precisar se movimentar ou mudar a respiração.",
        listOf(
            "Olhe ao redor e escolha um objeto. Observe a cor, o formato e algum detalhe que você não tinha notado.",
            "Procure mais dois objetos diferentes e compare suas cores ou formas, sem pressa.",
            "Se quiser, preste atenção a um som do ambiente. Pode continuar olhando os objetos também.",
            "Escolha um detalhe para observar por mais um pouco. Você pode repetir a ideia ou encerrar."
        )
    ),
    WATER(
        "Água em pequenos goles",
        "Uma pausa para mudar o foco, se você puder beber agora.",
        "Respeite sua orientação de líquidos, inclusive se tiver restrição. Não há quantidade ou meta para beber aqui.",
        listOf(
            "Se beber água agora estiver de acordo com seu cuidado, deixe um copo por perto. Se não, escolha outra ideia.",
            "Se quiser, tome um pequeno gole devagar e note a temperatura da água.",
            "Você não precisa beber mais. Pode apenas segurar o copo e observar sua forma ou a sensação nas mãos.",
            "Apoie o copo e veja o que prefere fazer a seguir. Não é preciso terminar a água."
        )
    ),
    WALK(
        "Caminhar devagar",
        "Movimento leve, dentro do que já é confortável para você.",
        "Só se caminhar já for seguro no seu dia a dia. Use um local plano, sem obstáculos, sem correr ou subir escadas. Pare se sentir desconforto; ficar sentado também é uma opção.",
        listOf(
            "Se estiver confortável para caminhar, escolha um trecho curto e plano em um lugar seguro. Você pode optar por outra ideia.",
            "Dê alguns passos devagar, no seu ritmo habitual. Observe o caminho e não fique olhando para a tela enquanto anda.",
            "Se continuar confortável, caminhe mais um pouco ou faça uma pausa. Não precisa acelerar ou contar passos.",
            "Pare em um lugar seguro e descanse como preferir. Não há meta de esforço nem obrigação de continuar."
        )
    ),
    HANDS(
        "Ocupar as mãos",
        "Um desenho ou uma pequena tarefa, sem sair do lugar.",
        "Escolha algo simples e confortável. Não precisa ficar pronto ou ficar bonito.",
        listOf(
            "Se tiver papel por perto, escolha um lápis e faça alguns traços. Outra opção é dobrar uma toalha ou um papel.",
            "Continue com um desenho livre ou uma dobra simples. Repare nas linhas, na cor ou na textura do material.",
            "Se quiser, mude o desenho ou a dobra. Pode também apenas segurar um objeto confortável nas mãos.",
            "Observe o que fez e decida se quer continuar por conta própria, trocar de ideia ou encerrar."
        )
    )
}
