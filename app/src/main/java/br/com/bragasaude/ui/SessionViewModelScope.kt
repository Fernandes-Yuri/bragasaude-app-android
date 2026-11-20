package br.com.bragasaude.ui

import androidx.compose.runtime.*
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.ui.auth.AuthViewModel

val LocalPersonalMainViewModel = staticCompositionLocalOf<MainViewModel?> { null }
val LocalSessionAuth = staticCompositionLocalOf<AuthViewModel> { error("Sessão ausente") }

/** Every shell owns its navigation and ViewModels; clearing also cancels their jobs. */
@Composable
internal fun SessionViewModelScope(store: ActiveContextStore, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "SessionViewModelScope exige um ViewModelStoreOwner na composição."
    }
    val parentFactory = (parent as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
        ?: error("SessionViewModelScope exige um HasDefaultViewModelProviderFactory (Activity) na composição.")
    val owner = remember(parent, parentFactory) {
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore = ViewModelStore()
            override val defaultViewModelProviderFactory = parentFactory
            override val defaultViewModelCreationExtras: CreationExtras
                get() {
                    val parentExtras = (parent as HasDefaultViewModelProviderFactory)
                        .defaultViewModelCreationExtras
                    val extras = MutableCreationExtras(parentExtras)
                    extras[VIEW_MODEL_STORE_OWNER_KEY] = this
                    return extras
                }
        }
    }
    DisposableEffect(owner, store) {
        val remove = store.addSessionResetListener { owner.viewModelStore.clear() }
        onDispose { remove(); owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
