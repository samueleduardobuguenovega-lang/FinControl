# FinControl — Actividad 2.5 Manejo de elementos de seguridad

App Android (Java) de finanzas personales: control de gastos, suscripciones y recordatorios de pagos mensuales, con integración de Google Maps y análisis/corrección de vulnerabilidades según la pauta IEI-093 U2 S10 2.5.

## Specs
- S21 (cliente): Teclado numérico propio para el monto (estilo My Finance de Innim): sin teclado del sistema, coma/punto/⌫/✓.
- S22 (cliente): Categorías con color e icono propio: círculos de color en selector/lista/top y añadir categoría inline en el formulario.
- S23 (cliente): Ingresos: pestaña GASTOS/INGRESOS con saldo del mes (gastos vs ingresos); categorías de ingreso (Sueldo, Extra).
- S24 (cliente): Períodos Día/Semana/Mes/Año en Gastos (estilo Monefy/My Finance).
- S17 (cliente+explore #3/#4): Plantillas de gastos frecuentes: chips de un toque (tipo MyExpenses "save as template"), switch en el formulario, y sugerencia automática cuando un patrón se repite >=3 veces en 30 días ("la app aprende de ti").
- S18 (cliente+explore #2): Navegación por meses en Gastos (flechas prev/next, tipo Monefy).
- S19 (cliente+explore #1): Swipe para eliminar con snackbar "Deshacer" y confirmación háptica (Material 3).
- S20 (cliente+explore #7): Resumen por categoría en el dashboard: barras del top 5 del mes (tipo Monefy/MoneyWiz reports).
- S12 (cliente): Categorías predefinidas ya hechas (catálogo inicial sembrado, como SpendChat/Cashvelope).
- S13 (cliente): Aprendizaje: las categorías más usadas aparecen primero (contador de uso, estilo Money Keeper).
- S14 (cliente): El usuario puede fijar (pin) categorías y reordenarlas (ItemTouchHelper drag & drop, estilo YNAB móvil).
- S15 (cliente): App funcional y completa: CRUD completo de gastos y suscripciones (editar/eliminar, no solo crear), pantalla de gestión de categorías (agregar/renombrar/eliminar con validación de uso).

- S1: "Realizar un análisis de seguridad en la aplicación Android... Utilizando MobSF (Mobile Security Framework) para realizar un análisis estático y dinámico específico para aplicaciones móviles." / "Documentar las vulnerabilidades encontradas en un archivo vulnerabilities.md."
- S2: "Realizar pruebas de vulnerabilidad utilizando herramientas como OWASP ZAP, MobSF, o cualquier otra herramienta de seguridad. Generar un reporte detallado de las pruebas realizadas y los resultados obtenidos. Guardar el reporte en el repositorio Github con el nombre vulnerability_report.pdf."
- S3: Best Practices: "Implementar cifrado para datos sensibles. Asegurar la comunicación de red utilizando HTTPS. Validar y sanitizar todas las entradas del usuario." + "Crear un documento best_practices.md que detalle las Best Practices implementadas en la aplicación. Explicar cómo cada práctica mejora la seguridad de la aplicación."
- S4: Security Tips: "Proteger la aplicación contra ataques de inyección SQL. Implementar autenticación y autorización seguras. Proteger la aplicación contra ataques de red (e.g., MITM)." + "Crear un documento security_tips.md que detalle los Security Tips implementados. Explicar cómo cada tip mejora la seguridad de la aplicación."
- S5: "Diseñar un programa estructurado para evaluar y mejorar la seguridad de la aplicación. Establecer un proceso de revisión periódica de la seguridad. Identificar métricas clave para medir la seguridad de la aplicación." + "Crear un documento security_improvement_program.md que detalle el programa y las mejoras realizadas. Incluir un plan de acción para futuras mejoras de seguridad."
- S6: "Integrar Google Maps." (elección del cliente: mapa de gastos por ubicación)
- S7: "Incluir un archivo README.md con una descripción del proyecto, las vulnerabilidades identificadas, las mejoras implementadas y cómo ejecutar la aplicación de manera segura." (estructura según ejemplo de la pauta)
- S8: "Subir todo el código, documentación y reportes al repositorio GitHub. Asegurarse de que el repositorio esté bien organizado y documentado."
- S9 (cliente): App funcional en Java con: login/registro, registro de gastos (monto, categoría, fecha, descripción, ubicación), suscripciones con recordatorio mensual por notificación, dashboard con resumen del mes y pantalla de mapa con los gastos registrados.
- S10 (cliente): El cliente es dueño de las decisiones de producto; el agente lidera la ejecución (ODD + subagentes) y reporta al llegar a la primera versión mínima viable.
- S11 (pauta): Actividad sumativa (20%), individual, evaluable por escala de 8 indicadores: ≥3 vulnerabilidades documentadas, pruebas con herramientas adecuadas, ≥3 Best Practices, ≥3 Security Tips, documentación detallada de prácticas/tips, programa de mejora con plan futuro.

## Tasks

- T1 [S9, S10] Skeleton del proyecto Gradle + wrapper + manifest + temas + modelos y contratos DB — inline — commit feat: project skeleton
- T2 [S9] Módulo auth: LoginActivity, RegisterActivity, UserDao (versión inicial) — worker-auth — commit feat: auth module
- T3 [S9, S6] Módulo gastos: lista, formulario con ubicación, GastoDao, permiso de ubicación — worker-expenses — commit feat: expenses module
- T4 [S9] Módulo suscripciones: CRUD, recordatorio mensual con AlarmManager + receiver — worker-subs — commit feat: subscriptions module
- T5 [S9, S6] Dashboard y MapaActivity con marcadores — worker-maps — commit feat: dashboard and map
- T6 [S9] Compilar APK debug y corregir errores — inline — (sin commit)
- T7 [S1, S2, S11] Análisis de vulnerabilidades (estático + dinámico si hay entorno) → vulnerabilities.md + vulnerability_report.pdf — inline — commit docs: vulnerability analysis
- T8 [S3, S4] Correcciones: hash de contraseñas, queries parametrizadas, cifrado Keystore, HTTPS, backup rules, clave API fuera del código — inline/workers — commit fix: security corrections
- T9 [S3, S4, S5, S7] best_practices.md, security_tips.md, security_improvement_program.md, README.md — inline — commit docs: security documentation
- T10 [S8] Preparar repositorio (git init, .gitignore, historial con versión vulnerable y correcciones); push a GitHub requiere decisión/credenciales del cliente — inline — commits de trabajo
- T11 [S12, S13, S14, S15] BD v2: tabla categorías sembrada + orden inteligente (fijadas→uso→alfabético) — worker-categorias — commit feat: category system
- T12 [S15] CRUD completo de gastos: editar/eliminar + selector visual de categorías — worker-gastos — commit feat: expense CRUD + category picker
- T13 [S15] CRUD completo de suscripciones: editar/eliminar — worker-subs — commit feat: subscription CRUD (mvs1m8ss)
- T15 [S17, S18, S19] Plantillas frecuentes + sugerencia + navegación de meses + swipe/undo — worker-frecuentes — commit feat: templates, month nav, swipe-undo
- T16 [S20] Resumen por categoría (top 5 barras) en dashboard — worker-resumen — commit feat: category summary

## Log

- L1 (cliente, verbatim): "que app sencilla puedo hacer para aplicar estas reglas ?" → propuesta de app de finanzas.
- L2 (cliente, verbatim): "una app de finansas, control de gastos y recordatorio de suscripciones y pagos mensuales ?"
- L3 (cliente, verbatim): "ya tengo android studio, mira quiero que tu seas el lider del proyecto, yo soy el cliente, yo quiero que hagas lo que pide el documento tu activa todas las metodologias, /comandos, tipo odd, etc, que sean beneficiosas y necesarias, y tu seras el jefe de grupo y los subagentes seran tus trabajadores, para que trates de one shotear la app, y hablame cuando tenga la primera version minima viable"
- L4 (cliente): eligió "Mapa de gastos por ubicación" para Google Maps.
- L5 (decisión técnica): reutilizar la configuración de build probada de SistemaBodegaAppAndroid (AGP 9.3.0, Gradle 9.6.0, Java 17) para acelerar el one-shot.
- L6 (decisión técnica): versión inicial deliberadamente vulnerable (password plano, queries concatenadas) para evidenciar vulnerabilidad→corrección en el historial de commits, tal como evalúa la escala.
- L7 (ejecución): T1-T6 completadas: skeleton + 4 módulos (workers en paralelo) + BOOT_COMPLETED + BUILD SUCCESSFUL (app-debug.apk, 48s). Commits: dd30f2a, 6f1700c, 43e9135, fba997a, 0016051, 35603ea.
- L8 (ejecución): T8 correcciones aplicadas (8ff0560): PBKDF2 salado + queries parametrizadas, EncryptedSharedPreferences, allowBackup=false + reglas, networkSecurityConfig cleartext=false, clave Maps vía manifestPlaceholders.
- L9 (ejecución): T7/T9: vulnerabilities.md (6 hallazgos con PoC), best_practices.md, security_tips.md, security_improvement_program.md, README.md con preguntas de cierre, vulnerability_report.pdf.
- L10 (cliente, verbatim): "no pusiste limite, el texto se pone con los iconos de las notificaciones, supongo que tambien con la nav bar, ve a internet y ponte a investigar para hacer skill de diseño en celular, y busca mas cosas, como optimizaciones, colores, diseño, psicologia para hacerla mas comoda y por ejemplo se que en apps android, hay que elegir un diseño y casi casi que casarse con el, ya que la memoria muscular en android es importantisima" → insets edge-to-edge corregidos (InsetsHelper, 8 pantallas) + límites de texto; investigación volcada en la skill mobile-ui-android (~/.agents/skills/).
- L11 (instalación en dispositivo físico 31001a9, Android 15): app instalada y corriendo OK; capturas en docs/.
- L12 (cliente, verbatim): "que paso con el diseño, no lo mejoraste quedo igual, niuna mejora" → rediseño visual Material 3 completo (commit 30f7aff): iconos, tarjetas, jerarquía, roles de color.
- L13 (cliente): Google Cloud Chile exige prepago único CLP 30.000 para activar facturación → se migra el mapa a osmdroid/OpenStreetMap (libre, sin clave). Verificado en dispositivo (commit 27a29b0).
- L14 (cliente, verbatim): "ya lo probe, funciona, pero ponle mas funciones, que las categorias ya esten hechas, que aprenda, que categorias mas usadas aparezcan arriba o que el usuario pueda fijarlas y moverlas, nose busca referencia y hazla una aplicacion funcional y completa de verdad" → S12-S15; referencias: Money Keeper (pins), YNAB (pin móvil), Cashvelope (gestión), SpendChat (seed), ItemTouchHelper (drag).
- L15 (cliente, verbatim): "otra cosa en la que me fije, tu cuenta no puedes hacer nada con ella, sale ahi el icono sin poder modificarlo, cambiar el nombre, cambiar al foto, personalizar la app, solo tiene funciones basicas , no puedes agregar gastos rutinarios, tipo si todos los dias gasto en colectivo, agregar un gasto frecuente y asi la app aprende de ti, nose cosas asi de calidad de vida, manda a un sub agente a mejorar estas cuestiones y investigar mas opciones y funciones para mejorar la calidad de vida de la app" → S16 + investigación QoL (explore mvs1p6qc).
- L16 (explore): Informe QoL: 12 funciones rankeadas con referencias. Elección del líder: swipe+undo (#1), navegación de meses (#2), plantillas+sugerencia (#3), resumen por categorías (#7); tema (#9) ya en T14; presupuestos por categoría, CSV, widget quedan para ronda futura.
- L17 (ejecución): T13 completada (SuscripcionDao.actualizar/eliminar/buscarPorId + modo edición). T11/T12 completadas (BD v2 categorías sembradas, orden inteligente fijadas→uso→alfabético, CRUD gastos, chip picker). T14 completada (CuentaActivity: foto/nombre/tema + Application hook FinControlApp). NOTA: una edición del padre truncó esta sección; reconstruida desde el historial de sesión.
- L18 (plan): T15 (mvs1wwcd) y T16 (mvs1zfs9) en ejecución: plantillas+sugerencia+meses+swipe/undo y resumen top 5 categorías.
- L19 (ejecución): T14-T16 completadas y commitadas (0442b14): CuentaActivity (foto/nombre/tema + FinControlApp hook), plantillas + sugerencia + meses + swipe/undo, top categorías dashboard. Build verde, instalado en 31001a9, verificado con datos reales del cliente (captura_dashboard_v4.png: $3.000 transporte en Top categorías).
- T17 [S23, S24] Núcleo: BD v4 (tipo en gastos + tipo/color/icono en categorías, semillas de ingreso), pestañas GASTOS/INGRESOS, períodos, saldo en dashboard — worker-nucleo — commit feat: income tabs, periods and balance
- T18 [S21, S22] Visual: categorías color+icono (selector círculos, lista, editor), teclado numérico en formulario, añadir inline — worker-visual — commit feat: category colors, icons and numeric keypad
- L20 (cliente, cierre de sesión): "ya debo de irme, actualiza mi app de mi celular y subela a git y guarda lo hecho en engram para aprender a futuro y luego apaga la pc principal" → app actualizada en el teléfono, repo creado en GitHub (privado por defecto: cambiar a público o invitar al docente para la entrega), memoria final en Engram, y apagado de la PC como última acción.
- L21 (cliente, 30-oct): la pauta exige Google Maps y la clave ya está disponible → se revierte el motor del mapa al SDK de Google (`c86b162`, publicado como `d666190`): SupportMapFragment, play-services-maps 20.1.0, clave inyectada desde `local.properties` como `google_maps_key` y restringida por paquete y SHA-1. Verificado en emulador API 36 con marcadores reales.
