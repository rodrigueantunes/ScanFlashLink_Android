package com.antunes.scanflashlink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions




@OptIn(ExperimentalGetImage::class)
class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textureView: TextureView
    private lateinit var txtScanResult: TextView
    private lateinit var edtServerIp: EditText
    private lateinit var btnSaveIp: Button
    private lateinit var sharedPreferences: SharedPreferences
    private var serverIp: String? = null
    private val SERVER_PORT = 12345
    private var isProcessing = false // Évite les scans en boucle

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisation des vues
        textureView = findViewById(R.id.cameraPreview)
        txtScanResult = findViewById(R.id.txtScanResult)
        edtServerIp = findViewById(R.id.edtServerIp)
        btnSaveIp = findViewById(R.id.btnSaveIp)

        // Récupération et affichage de l'IP enregistrée
        sharedPreferences = getSharedPreferences("ScanFlashLinkPrefs", MODE_PRIVATE)
        serverIp = sharedPreferences.getString("SERVER_IP", "")
        edtServerIp.setText(serverIp)

        btnSaveIp.setOnClickListener {
            val ip = edtServerIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                serverIp = ip
                sharedPreferences.edit().putString("SERVER_IP", ip).apply()
                Toast.makeText(this, "IP enregistrée : $ip", Toast.LENGTH_SHORT).show()
                Log.d("ScanFlashLink", "IP du serveur mise à jour: $ip")
            } else {
                Toast.makeText(this, "Veuillez entrer une IP valide", Toast.LENGTH_SHORT).show()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Vérification et demande de permission pour la caméra
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }




    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    /** Demande l'autorisation d'utiliser la caméra **/
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    /** Gère la réponse de l'utilisateur à la demande de permission **/
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                restartApp()
            } else {
                Toast.makeText(this, "Permission de la caméra refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Redémarre l'application après l'octroi de la permission **/
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(Surface.ROTATION_0) // ✅ Toujours en mode portrait
                .build()
                .also {
                    it.setSurfaceProvider { request ->
                        val surfaceTexture = textureView.surfaceTexture
                        if (surfaceTexture != null) {
                            val surface = Surface(surfaceTexture)
                            request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {}
                        }
                    }
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(Surface.ROTATION_0) // ✅ On scanne toujours en mode portrait
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll() // Libération des ressources avant de réattacher la caméra
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("CameraX", "Erreur de liaison de la caméra", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Nettoie le résultat d'un code-barres GS1.
     * Cette fonction retire le préfixe "02" (s'il existe) et supprime les séparateurs FNC1.
     */
    fun parseGS1Barcode(raw: String): String {
        var result = raw
        // Exemple de nettoyage : retirer le préfixe "02" si présent
        //if(result.startsWith("02")) {
        //    result = result.substring(2)
        //}
        // Supprimer le caractère FNC1 (ASCII 29)
        result = result.replace("\u001D", "")
        return result
    }


    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        try {
            val image = imageProxy.image
            if (image != null) {
                val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_AZTEC,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_PDF417,
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E
                    )
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isEmpty()) {
                            runOnUiThread {
                                findViewById<BarcodeOverlayView>(R.id.barcodeOverlay).setBarcodeRect(null)
                            }
                        } else {
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { rawResult ->
                                    // Retirer le préfixe "]C1" s'il est présent
                                    val withoutC1 = if (rawResult.startsWith("]C1")) {
                                        rawResult.substring(3)
                                    } else {
                                        rawResult
                                    }
                                    // Appliquer le nettoyage GS1
                                    val scanResult = parseGS1Barcode(withoutC1)

                                    isProcessing = true // Bloquer les scans successifs

                                    // Récupérer et convertir la bounding box du code
                                    val boundingBox = barcode.boundingBox
                                    boundingBox?.let {
                                        val scaleX = textureView.width.toFloat() / image.height.toFloat()
                                        val scaleY = textureView.height.toFloat() / image.width.toFloat()
                                        val mappedRect = Rect(
                                            (it.left * scaleX).toInt(),
                                            (it.top * scaleY).toInt(),
                                            (it.right * scaleX).toInt(),
                                            (it.bottom * scaleY).toInt()
                                        )
                                        runOnUiThread {
                                            findViewById<BarcodeOverlayView>(R.id.barcodeOverlay)
                                                .setBarcodeRect(mappedRect)
                                        }
                                    }

                                    txtScanResult.text = "Scanné : $scanResult"
                                    sendToServer(scanResult)

                                    val bitmap = textureView.bitmap
                                    if (bitmap != null) {
                                        showScannedScreen(bitmap)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("CameraX", "Erreur lors du scan du code-barres", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun showScannedScreen(bitmap: Bitmap) {
        runOnUiThread {
            val dialogView = layoutInflater.inflate(R.layout.dialog_scanned_screen, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.scannedImageView)
            val btnOk = dialogView.findViewById<Button>(R.id.btnOk)

            imageView.setImageBitmap(bitmap)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            btnOk.setOnClickListener {
                dialog.dismiss()
                isProcessing = false
            }

            dialog.show()
        }
    }

    private fun sendToServer(code: String) {
        if (serverIp.isNullOrEmpty()) return

        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(serverIp, SERVER_PORT), 5000)

                val outputStream = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
                outputStream.println(code)
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
