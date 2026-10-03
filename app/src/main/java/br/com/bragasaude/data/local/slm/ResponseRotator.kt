package br.com.bragasaude.data.local.slm

import javax.inject.Inject
import javax.inject.Singleton

/** Fila embaralhada por gravidade; evita repetição inclusive entre ciclos. */
@Singleton
class ResponseRotator @Inject constructor() {
    private val remaining = mutableMapOf<TriageSeverity, MutableList<String>>()
    private val previous = mutableMapOf<TriageSeverity, String>()
    private val pools = mapOf(
        TriageSeverity.EMERGENCIA to listOf(
            "Atenção: esses sintomas precisam de socorro agora. Toque no botão do SAMU 192 na tela ou peça a alguém próximo para ligar imediatamente.",
            "Não espere esses sintomas passarem. Ligue agora para o SAMU 192 pelo botão na tela e peça ajuda a alguém próximo.",
            "Pare o que estiver fazendo e procure socorro agora. O botão do SAMU 192 está na tela para você ligar.",
            "Esses sinais exigem atendimento imediato. Ligue para o SAMU 192 agora e siga a orientação da equipe de socorro.",
            "Peça a alguém para ficar com você. Ligue imediatamente para o SAMU 192 pelo botão na tela."
        ),
        TriageSeverity.URGENCIA to listOf(
            "Identifiquei um relato de acidente. Se não conseguir se levantar com segurança ou estiver sangrando, não faça esforço. Chame um familiar ou ligue para o SAMU 192.",
            "Esse acidente merece atenção. Evite movimentos se estiver com dor ou machucado e peça ajuda a alguém próximo. Se precisar de socorro, ligue 192.",
            "Vamos cuidar da sua segurança agora. Não force o corpo se estiver machucado. Avise um familiar ou use o botão do SAMU 192 para pedir socorro.",
            "Peça a alguém para acompanhar você após esse acidente. Se bateu a cabeça, procure avaliação médica. Se não conseguir se mover com segurança, ligue 192.",
            "Não enfrente esse acidente sozinho. Se houver sangramento contínuo ou dificuldade para se levantar, peça socorro a um familiar ou disque 192."
        ),
        TriageSeverity.GRAVE to listOf(
            "Esse relato merece atenção médica hoje. Peça a alguém para acompanhar você e procure uma UPA. Se houver piora importante, ligue para o SAMU 192.",
            "Esses sinais precisam de avaliação hoje. Evite esforço e peça apoio a um familiar para procurar atendimento. Se piorar, ligue 192.",
            "É importante procurar orientação médica hoje. Uma UPA pode avaliar esses sinais. Se aparecer dor no peito, desmaio ou dificuldade para respirar, ligue 192 imediatamente.",
            "Vamos dar atenção a esses sinais. Se puder fazer isso com segurança, confira a medida, sem adiar o atendimento hoje. Peça apoio a alguém próximo.",
            "Procure avaliação médica hoje para esse relato. Peça a um familiar para acompanhar você. Se o mal-estar aumentar ou precisar de socorro imediato, ligue 192."
        ),
        TriageSeverity.POUCO_URGENTE to listOf(
            "Entendi o seu incômodo. Acomode-se em um lugar confortável e descanse um pouco. Se persistir ou piorar, converse com seu médico.",
            "Sinto muito por esse desconforto. Faça uma pausa e, se puder beber água normalmente, hidrate-se. Se não melhorar ou piorar, procure orientação médica.",
            "Vamos cuidar desse incômodo com calma. Descanse em uma posição confortável. Se o sintoma continuar ou aumentar, converse com seu médico.",
            "Esses desconfortos podem cansar. Reserve um momento para repousar e observe como se sente. Se persistir ou piorar, procure seu médico.",
            "Estou aqui com você. Evite esforço enquanto estiver desconfortável e descanse um pouco. Se não aliviar ou houver piora, procure avaliação médica."
        )
    )

    @Synchronized
    fun next(severity: TriageSeverity): String {
        val pool = requireNotNull(pools[severity]) { "Não há resposta clínica para NENHUMA" }
        val queue = remaining.getOrPut(severity) { mutableListOf() }
        if (queue.isEmpty()) {
            queue.addAll(pool.shuffled())
            if (queue.first() == previous[severity]) java.util.Collections.swap(queue, 0, 1)
        }
        return queue.removeAt(0).also { previous[severity] = it }
    }
}
