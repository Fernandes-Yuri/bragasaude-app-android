package br.com.bragasaude.domain

/**
 * DT-052: desfecho do salvamento de uma medição, exposto às telas para o
 * indicador visual de sincronização. O usuário precisa enxergar se a medição
 * já chegou à nuvem antes de fechar o app ou sair da conta (persistência
 * imediata percebida, não apenas otimista).
 */
enum class VitalSaveStatus {
    /** Gravação no Room + envio ao backend em andamento. */
    SAVING,

    /** Backend confirmou a medição (resposta com id). */
    SYNCED,

    /** Gravada no aparelho, aguardando rede — WorkManager reenvia sozinho. */
    PENDING_RETRY,
}
