package com.fincontrol.app.suscripciones;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.fincontrol.app.R;
import com.fincontrol.app.model.Suscripcion;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Recordatorio de pago mensual.
 *
 * Una sola alarma repetitiva inexacta suena todos los días a las 09:00 hora local; en cada
 * tick, cada suscripción activa que cobra el día de hoy genera una notificación. Las alarmas
 * inexactas no piden permisos especiales y son la opción que menos batería gasta.
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    /** Id del canal de notificaciones que pide el enunciado. */
    public static final String CANAL_RECORDATORIOS = "recordatorios_pagos";

    /** Hora local del chequeo diario. */
    private static final int HORA_RECORDATORIO = 9;

    /** Request code fijo para que programarDiario() no apile alarmas duplicadas. */
    private static final int ID_ALARMA = 9001;

    /** Id base de las notificaciones, una por suscripción. */
    private static final int ID_NOTIFICACION_BASE = 2000;

    /** Programa (o refresca) el chequeo diario. Se puede llamar en cada inicio sin problema. */
    public static void programarDiario(Context contexto) {
        Context ctx = contexto.getApplicationContext();
        AlarmManager alarmas = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmas == null) {
            Log.w(TAG, "AlarmManager no disponible; no se programo el recordatorio");
            return;
        }
        Intent intento = new Intent(ctx, ReminderReceiver.class);
        PendingIntent pendiente = PendingIntent.getBroadcast(ctx, ID_ALARMA, intento,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmas.setInexactRepeating(AlarmManager.RTC_WAKEUP, proximoDisparo(), AlarmManager.INTERVAL_DAY, pendiente);
    }

    /** Próximas 09:00 hora local (mañana si la de hoy ya pasó). */
    private static long proximoDisparo() {
        Calendar calendario = Calendar.getInstance();
        calendario.set(Calendar.HOUR_OF_DAY, HORA_RECORDATORIO);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        if (calendario.getTimeInMillis() <= System.currentTimeMillis()) {
            calendario.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendario.getTimeInMillis();
    }

    // puedeNotificar() ya controla el permiso POST_NOTIFICATIONS en runtime.
    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                // Al reiniciar se borran las alarmas: se vuelve a armar el chequeo diario.
                programarDiario(context);
                return;
            }
            Context ctx = context.getApplicationContext();
            if (!puedeNotificar(ctx)) {
                Log.i(TAG, "Notificaciones deshabilitadas o sin permiso; no se envian recordatorios");
                return;
            }
            crearCanal(ctx);

            int diaHoy = LocalDate.now().getDayOfMonth();
            List<Suscripcion> vencenHoy = new SuscripcionDao(ctx).listarPorDiaCobro(diaHoy);
            NotificationManagerCompat gestor = NotificationManagerCompat.from(ctx);
            for (Suscripcion suscripcion : vencenHoy) {
                gestor.notify(idNotificacion(suscripcion), construirNotificacion(ctx, suscripcion));
            }
            Log.i(TAG, "Recordatorios enviados: " + vencenHoy.size());
        } catch (Exception e) {
            // Un receiver no puede botar el proceso: se registra y la alarma reintenta mañana.
            Log.e(TAG, "Error al enviar los recordatorios", e);
        }
    }

    /** POST_NOTIFICATIONS (Android 13+) y además el interruptor global de notificaciones. */
    private static boolean puedeNotificar(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    private static void crearCanal(Context ctx) {
        NotificationManager gestor = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (gestor == null) {
            return;
        }
        NotificationChannel canal = new NotificationChannel(CANAL_RECORDATORIOS,
                ctx.getString(R.string.recordatorio_channel), NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription(ctx.getString(R.string.recordatorio_channel));
        gestor.createNotificationChannel(canal);
    }

    private static Notification construirNotificacion(Context ctx, Suscripcion suscripcion) {
        String monto = String.format(Locale.US, "%.2f", suscripcion.getMonto());
        String dia = "d\u00eda " + suscripcion.getDiaCobro();
        String texto = ctx.getString(R.string.recordatorio_texto, suscripcion.getNombre(), dia, monto);

        Intent abrir = new Intent(ctx, SuscripcionesActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contenido = PendingIntent.getActivity(ctx, idNotificacion(suscripcion), abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(ctx, CANAL_RECORDATORIOS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(ctx.getString(R.string.recordatorio_titulo))
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contenido)
                .setAutoCancel(true)
                .build();
    }

    /** Un id por suscripción, así varios pagos quedan visibles a la vez. */
    private static int idNotificacion(Suscripcion suscripcion) {
        return ID_NOTIFICACION_BASE + (int) (suscripcion.getId() % 100000L);
    }
}
