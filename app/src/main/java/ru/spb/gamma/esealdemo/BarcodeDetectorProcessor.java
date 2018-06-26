package ru.spb.gamma.esealdemo;

import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import ru.spb.gamma.esealdemo.ui.camera.CameraSource;
import ru.spb.gamma.esealdemo.ui.camera.GraphicOverlay;

public class BarcodeDetectorProcessor implements BarcodeDetector.Processor<Barcode> {

        private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;
        private  CameraSource mCamera;

        BarcodeDetectorProcessor(GraphicOverlay<BarcodeGraphic> graphicOverlay) {
            mGraphicOverlay = graphicOverlay;
        }

        /**
         * Called by the detector to deliver detection results.
         * If your application called for it, this could be a place to check for
         * equivalent detections by tracking TextBlocks that are similar in location and content from
         * previous frames, or reduce noise by eliminating TextBlocks that have not persisted through
         * multiple detections.
         */
        @Override
        public void receiveDetections(Detector.Detections<Barcode> detections) {
            mGraphicOverlay.clear();
            SparseArray<Barcode> items = detections.getDetectedItems();
            for (int i = 0; i < items.size(); ++i) {
                Barcode item = items.valueAt(i);
                BarcodeGraphic graphic = new BarcodeGraphic(mGraphicOverlay, item);
                mGraphicOverlay.add(graphic);
//                mCamera.stop();
//                break;
            }
        }

        /**
         * Frees the resources associated with this detection processor.
         */
        @Override
        public void release() {
            mGraphicOverlay.clear();
        }

    public void setCameraSource(CameraSource cameraSource) {
            mCamera =  cameraSource;
    }
}

