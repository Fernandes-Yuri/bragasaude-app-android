package br.com.bragasaude.ai

import br.com.bragasaude.domain.VoiceSessionCommand
import java.time.Instant
import java.time.ZoneId

/** Continuidade de respostas locais; nunca executa a ação citada na resposta. */
internal object BragaContextualHelp {
    enum class Request { SIMPLIFY, REPEAT, LAST_PART, DATE }
    private val prefix = Regex("""^(?:(?:entendi|compreendi|entendido|ok|legal|certo|beleza|ta bom|(?:muito )?obrigad[oa]|valeu)[\s,!.;]+)+(?:mas\s+|so que\s+)?""")
    fun request(text: String): Request? {
        val input = BragaRoutingPolicy.normalize(text).replace(prefix, "").trimEnd('.', '!', '?', ' ')
            .removePrefix("por favor ").removeSuffix(" por favor").trim()
        if (VoiceSessionCommand.parse(text) == VoiceSessionCommand.REPEAT) {
            return if (Regex("""\b(ultima (?:parte|frase)|o final)\b""").containsMatchIn(input)) Request.LAST_PART else Request.REPEAT
        }
        return when {
            Regex("""^(?:nao entendi(?: nada(?: do que voce explicou)?)?|ainda nao entendi|(?:ainda )?nao ficou claro|(?:me )?(?:explica|explique)(?: melhor| de novo| de um jeito mais simples)|(?:voce )?pode explicar(?: melhor| de um jeito mais simples)?)$""").matches(input) -> Request.SIMPLIFY
            Regex("""^(?:isso (?:e|foi) de hoje|esse (?:registro|valor) (?:e|foi) de hoje)$""").matches(input) -> Request.DATE
            Regex("""^(?:repete|repita|repetir|pode repetir) (?:a )?ultima (?:parte|frase)$|^nao ouvi o final$""").matches(input) -> Request.LAST_PART
            Regex("""^(?:(?:(?:voce )?(?:pode|consegue) )?(?:repete|repita|repetir|falar de novo|dizer de novo)(?: (?:a )?(?:sua )?ultima (?:resposta|mensagem)(?: que (?:voce )?(?:falou|disse))?| o que (?:voce )?(?:falou|disse))?|nao ouvi)$""").matches(input) -> Request.REPEAT
            else -> null
        }
    }

    fun answer(request: Request, previous: NluOutput?, nowMillis: Long): NluOutput {
        fun clarify(reply: String) = NluOutput("entrada_explicacao_sem_referencia", reply)
        if (previous == null) return clarify("Qual informação você quer que eu explique ou repita? Não tenho uma resposta local recente nesta conversa para usar como referência.")
        val text = previous.respostaLocal.orEmpty()
        return when (request) {
            Request.REPEAT -> NluOutput("ajuda_repeticao_contextual", text)
            Request.LAST_PART -> {
                // Limites de frase, preservando decimais e o texto original dos valores.
                val last = text.split(Regex("""(?<=[.!?])\s+(?=\p{Lu})""")).lastOrNull().orEmpty()
                NluOutput("ajuda_repeticao_contextual", last)
            }
            Request.DATE -> {
                val date = previous.referenceMeasuredAtMillis
                val zone = previous.referenceZoneId
                if (date == null || zone == null) clarify("A resposta anterior não identifica uma única medição com data. Você quer consultar a última pressão, a última glicemia ou os registros de hoje?")
                else {
                    val zoneId = ZoneId.of(zone)
                    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
                    val measured = Instant.ofEpochMilli(date).atZone(zoneId).toLocalDate()
                    NluOutput("ajuda_data_registro_contextual",
                        (if (measured == today) "Sim, a medição mencionada foi feita hoje." else "Não, a medição mencionada não foi feita hoje.") + " Na resposta anterior: $text")
                }
            }
            Request.SIMPLIFY -> {
                val simple = when (previous.intent) {
                    "explicacao_variacao_pressao" -> "A pressão pode mudar por vários motivos, como estresse, café, exercício e a forma de medir. O registro sozinho não mostra a causa no seu caso. Meça em repouso e procure o profissional que acompanha você se a mudança persistir. Não altere seus remédios por conta própria."
                    "explicacao_variacao_glicemia" -> "Alimentação, atividade física, estresse e doenças podem mudar a glicemia. O registro sozinho não mostra a causa no seu caso. Compare medições feitas em condições semelhantes e converse com o profissional que acompanha você. Não ajuste medicamentos por conta própria."
                    "ajuda_anexar_exame" -> "Abra Meus Exames e toque em Adicionar exame. Escolha foto, arquivo ou preenchimento manual. Revise antes de salvar. Esta conversa não anexa nem salva o exame."
                    "ajuda_relatorio_app" -> "Abra Dados e Relatórios. Toque em Baixar Relatório em PDF. Ele reúne os últimos 30 dias. Abra o arquivo num leitor de PDF; o compartilhamento depende das opções desse leitor."
                    "ajuda_horario_remedio" -> "Abra Remédios no menu superior. Confira o medicamento e o horário antes de salvar. Mantenha os horários orientados na sua receita."
                    "ajuda_meta_agua" -> "Na tela de hidratação, toque em Editar meta de hidratação. Ajuste o valor e toque em Salvar Meta. Se você tem restrição de líquidos, siga a meta orientada pelo profissional que acompanha você."
                    "ajuda_corrigir_registro" -> if (text.contains("Desfazer")) "Antes de salvar, revise a quantidade de água. Para remover o último registro, confira qual é e use Desfazer na tela de hidratação. Esta conversa não alterou os dados."
                        else "Na confirmação da medição, toque em Corrigir e revise antes de salvar. Para pressão e glicemia já salvas, essa tela não edita registros anteriores. Esta conversa não alterou os dados."
                    "ajuda_medicao_pressao" -> "Descanse sentado por 5 minutos. Apoie costas, pés e braço; mantenha o braço na altura do coração. Evite café, cigarro e exercício nos 30 minutos anteriores. Use um aparelho de braço adequado e siga suas instruções. O app guarda a anotação; ele não mede a pressão."
                    "ajuda_registro_sem_dados" -> "Confira a conta e se salvou a medição. Um período sem anotações não mostra valores. Confira também a tela desses dados. Esta mensagem não permite afirmar que um registro foi perdido."
                    "ajuda_exame_sem_dados" -> "Em Meus Exames, confira a busca e o filtro de armazenamento. Verifique a conta e se concluiu a revisão e o salvamento. Esta mensagem não permite afirmar que o exame foi perdido."
                    "ajuda_historico_app" -> "Peça sua última pressão, a glicemia de ontem ou quanto bebeu de água hoje. Para um PDF, abra Dados e Relatórios e toque em Baixar Relatório em PDF."
                    BragaHealthMemory.PRESSURE, BragaHealthMemory.GLUCOSE, BragaHealthMemory.WATER -> if (previous.hasLocalData == true)
                        "Eu mostrei o resultado da consulta aos seus registros no aplicativo. Isso não explica a causa de uma mudança. Você quer que eu esclareça os valores, o período ou como consultar esses registros?"
                        else "Não consegui apresentar registros nessa consulta. Você quer esclarecer o período consultado ou como conferir os dados na tela do aplicativo?"
                    else -> return clarify("Qual parte da resposta você quer esclarecer: uma medição, um registro ou uma função do aplicativo?")
                }
                NluOutput("ajuda_reformulacao_contextual", simple)
            }
        }
    }
}
