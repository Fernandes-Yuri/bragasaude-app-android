package br.com.bragasaude.ui.theme

import androidx.compose.ui.graphics.Color

// === PALETA OFICIAL BRAGA SAÚDE — LUMINOSA, LEVE E ACOLHEDORA ===
val BragaEmerald = Color(0xFF00897B)        // Verde Teal/Esmeralda Luminoso (não escuro/sombrio)
val BragaEmeraldDark = Color(0xFF00695C)    // Tom intermediário suave para gradientes leves
val BragaEmeraldLight = Color(0xFF26A69A)   // Destaque luminoso acolhedor
val BragaMint = Color(0xFFE0F2F1)           // Verde menta bem clarinho e fresco
val BragaMintBorder = Color(0xFFB2DFDB)     // Borda sutil e suave
val BragaMintSurface = Color(0xFFF4FBF9)    // Superfície limpa e fresca
// Emergência Suavizada (terracota acolhedor, menos agressivo/gritante)
val BragaEmergencyOrange = Color(0xFFD9532F)// Terracota acolhedor
val BragaEmergencyLight = Color(0xFFFFF5F2) // Fundo suave para alertas

// Neutros claros e arejados (Acessibilidade Sênior)
val BragaBackground = Color(0xFFFAFCFB)     // Fundo quase branco, limpo e aberto
val BragaCardSurface = Color(0xFFFFFFFF)    // Branco puro para elevação limpa
val BragaCardBorder = Color(0xFFE5EBE8)     // Borda ultra-leve
val BragaTextPrimary = Color(0xFF1E293B)    // Slate 800 - alta nitidez sem ser agressivo
val BragaTextSecondary = Color(0xFF64748B)  // Slate 500 - legendas confortáveis

// Aliases para compatibilidade retroativa
val TealPrimary = BragaEmerald
val TealSecondary = BragaEmeraldLight
val TealLight = BragaMint
val TealSurface = BragaMintSurface
val Background = BragaBackground
val OnBackground = BragaTextPrimary
val Surface = BragaCardSurface
val SurfaceVariant = BragaMint
val OnSurface = BragaTextPrimary
val OnSurfaceVariant = BragaTextSecondary
val OnPrimary = Color.White

// Cores semânticas
val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
val Info = Color(0xFF0EA5E9)

val AccentRed = BragaEmergencyOrange
val AccentRedLight = BragaEmergencyLight
val PrimaryRed = AccentRed
val HeartWhite = OnPrimary
val CrossRed = AccentRed
