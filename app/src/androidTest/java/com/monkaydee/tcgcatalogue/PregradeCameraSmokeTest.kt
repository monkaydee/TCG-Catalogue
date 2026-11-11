package com.monkaydee.tcgcatalogue

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Virtual hardware verifies binding, frame delivery, still capture and teardown, not optical quality. */
@RunWith(AndroidJUnit4::class)
class PregradeCameraSmokeTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private class Owner:LifecycleOwner { override val lifecycle=LifecycleRegistry(this) }
    @Test fun captureAndAnalysisCanReopenAfterLifecycleTeardown() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val provider=ProcessCameraProvider.getInstance(context).get(30,TimeUnit.SECONDS)
        val executor=Executors.newSingleThreadExecutor()
        try {
            repeat(2) {
                val owner=Owner();val frames=CountDownLatch(3);val photo=CountDownLatch(1)
                val failure=AtomicReference<Throwable>()
                val capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    if (image.width<=0 || image.height<=0 || image.cropRect.isEmpty) failure.set(AssertionError("Invalid preview geometry"))
                    frames.countDown();image.close()
                }
                instrumentation.runOnMainSync {
                    owner.lifecycle.currentState=Lifecycle.State.RESUMED
                    provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,capture,analysis)
                }
                assertTrue("Analysis frames did not arrive",frames.await(30,TimeUnit.SECONDS))
                instrumentation.runOnMainSync {
                    capture.takePicture(ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image:ImageProxy) {
                            try { if (image.width<=0 || image.height<=0 || image.cropRect.isEmpty) failure.set(AssertionError("Invalid still geometry")) }
                            finally { image.close();photo.countDown() }
                        }
                        override fun onError(error:ImageCaptureException) { failure.set(error);photo.countDown() }
                    })
                }
                assertTrue("Still capture did not finish",photo.await(30,TimeUnit.SECONDS))
                assertNull("Capture/analysis failed",failure.get())
                instrumentation.runOnMainSync { provider.unbindAll();owner.lifecycle.currentState=Lifecycle.State.DESTROYED }
            }
        } finally { instrumentation.runOnMainSync {provider.unbindAll()};executor.shutdown() }
    }
}
