# Mi gym

App Android para anotar los pesos del gimnasio. Eliges la rutina del día, anotas tus series y ves al lado lo que hiciste la vez pasada, tus récords por repeticiones y tus superseries. Funciona sin internet y guarda todo en el celular.

## Instalar o actualizar

Escanea el código con la cámara del celular o abre el enlace:
**https://github.com/gaqlh/mi-gym/releases/latest/download/MiGym.apk**

<img src="docs/qr-descarga.svg" width="220" alt="Código QR para descargar la última versión">

1. Abre el archivo descargado y permite «Instalar apps desconocidas» para el navegador.
2. Toca Instalar. Si Google Play Protect no reconoce la app, toca «Más detalles» › «Instalar de todas formas».
3. Para actualizar, instala la versión nueva encima de la anterior: tus datos se mantienen. Si desinstalas la app, se borran.

La app revisa sola al abrirse si hay una versión nueva y te avisa en el inicio.

## Para programar

- `app/app-gym.html`: toda la app. El mismo archivo se publica como Artifact de Claude para probarla en la computadora.
- `app/android/`: la app Android que envuelve esa página (WebView), sin Android Studio.
  - Compilar: `VERSION_CODE=2 VERSION_NAME=0.2 bash app/android/compilar.sh`
  - Publicar una versión nueva: `bash app/android/publicar.sh 0.3 "Qué cambió"`
- La firma (`app/android/firma/`) no está en el repositorio. Guarda una copia aparte: sin ella no se puede actualizar la app instalada.
