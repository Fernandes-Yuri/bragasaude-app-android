package br.com.bragasaude.data.medicationdoses

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MedicationDoseWorker @AssistedInject constructor(@Assisted context:Context,
    @Assisted params:WorkerParameters,private val repository:MedicationDoseRepository):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=if(repository.flush()) Result.success() else Result.retry()
}
