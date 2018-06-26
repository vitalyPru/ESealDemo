package ru.spb.gamma.esealdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.google.android.gms.internal.vision.zzad;
import com.google.android.gms.internal.vision.zzae;
import com.google.android.gms.internal.vision.zzb;
import com.google.android.gms.internal.vision.zzm;
import com.google.android.gms.internal.vision.zzo;
import com.google.android.gms.internal.vision.zzx;
import com.google.android.gms.internal.vision.zzz;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextDetector;
//import com.google.android.gms.vision.text.zzb;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class MLOcrDetector extends Detector<TextBlock> {
    private  MLOcrDetector() {
            throw new IllegalStateException("Default constructor called");
    }

    private final zzad zzdf;
    FirebaseVisionTextDetector mDetector;

    private MLOcrDetector(zzad var1, FirebaseVisionTextDetector detector) {
        this.mDetector = detector;
        this.zzdf = var1;
    }

    public final SparseArray<TextBlock> detect(Frame frame) {
        zzz var4 = new zzz(new Rect());
        if (frame == null) {
            throw new IllegalArgumentException("No frame supplied.");
        } else {
            zzm var5 = zzm.zzc(frame);
            Bitmap bitmap;
            int var9;
            int width;
            int frameFormat;

            FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                    .setWidth(1280)
                    .setHeight(720)
                    .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                    .setRotation(0)
                    .build();

            ByteBuffer buffer = frame.getGrayscaleImageData();
            FirebaseVisionImage image = FirebaseVisionImage.fromByteBuffer(buffer, metadata);

            Task<FirebaseVisionText> result =
                    mDetector.detectInImage(image)
                            .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                                @Override
                                public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                    // Task completed successfully
                                    // ...
                                    for (FirebaseVisionText.Block block: firebaseVisionText.getBlocks()) {
                                        Rect boundingBox = block.getBoundingBox();
                                        Point[] cornerPoints = block.getCornerPoints();
                                        String text = block.getText();

                                        for (FirebaseVisionText.Line line: block.getLines()) {
                                            // ...
                                            for (FirebaseVisionText.Element element: line.getElements()) {
                                                // ...
                                            }
                                        }
                                    }
                                }
                            })
                            .addOnFailureListener(
                                    new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            // Task failed with an exception
                                            // ...
                                        }
                                    });

// Or: FirebaseVisionImage image = FirebaseVisionImage.fromByteArray(byteArray, metadata);
/*
            if (frame.getBitmap() != null) {
                bitmap = frame.getBitmap();
            } else {
                Frame.Metadata frameMetadata = frame.getMetadata();
                ByteBuffer byteBuffer = frame.getGrayscaleImageData();
                frameFormat = frameMetadata.getFormat();
                int var11 = var5.height;
                width = var5.width;
                var9 = frameFormat;
                ByteBuffer var8 = byteBuffer;
                byte[] var13;
                if (byteBuffer.hasArray() && var8.arrayOffset() == 0) {
                    var13 = var8.array();
                } else {
                    var13 = new byte[var8.capacity()];
                    var8.get(var13);
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                (new YuvImage(var13, var9, width, var11, (int[])null)).compressToJpeg(new Rect(0, 0, width, var11), 100, outputStream);
                byte[] byteArray;
                bitmap = BitmapFactory.decodeByteArray(byteArray = outputStream.toByteArray(), 0, byteArray.length);
            }

            bitmap = zzo.zzb(bitmap, var5);
            if (!var4.zzdr.isEmpty()) {
                Rect var25 = var4.zzdr;
                frameFormat = frame.getMetadata().getWidth();
                width = frame.getMetadata().getHeight();
                var9 = frameFormat;
                Rect var18 = var25;
                switch(var5.rotation) {
                    case 1:
                        var25 = new Rect(width - var18.bottom, var18.left, width - var18.top, var18.right);
                        break;
                    case 2:
                        var25 = new Rect(var9 - var18.right, width - var18.bottom, var9 - var18.left, width - var18.top);
                        break;
                    case 3:
                        var25 = new Rect(var18.top, var9 - var18.right, var18.bottom, var9 - var18.left);
                        break;
                    default:
                        var25 = var18;
                }

                Rect var17 = var25;
                var4.zzdr.set(var17);
            }

            var5.rotation = 0;
            zzx[] var19 = this.zzdf.zza(bitmap, var5, var4);
            SparseArray var20 = new SparseArray();
            zzx[] var21 = var19;
            int var12 = var19.length;

            for(int var23 = 0; var23 < var12; ++var23) {
                zzx var24 = var21[var23];
                SparseArray var15;
                if ((var15 = (SparseArray)var20.get(var24.zzdp)) == null) {
                    var15 = new SparseArray();
                    var20.append(var24.zzdp, var15);
                }

                var15.append(var24.zzdq, var24);
            }
*/
            SparseArray var22 = new SparseArray(/*var20.size()*/ 0);

//            for(var12 = 0; var12 < var20.size(); ++var12) {
//                var22.append(var20.keyAt(var12), new TextBlock((SparseArray)var20.valueAt(var12)));
//            }

            return var22;
        }

    }

    public final boolean isOperational() {
        return this.zzdf.isOperational();
    }

    public final void release() {
        super.release();
        this.zzdf.zzo();
    }

    public static class Builder {
        private Context zze;
        private zzae zzdg;
        private FirebaseVisionTextDetector mDetector;

        public Builder(Context context) {
            this.zze = context;
            this.zzdg = new zzae();
            FirebaseApp.initializeApp(context.getApplicationContext());
            mDetector = FirebaseVision.getInstance()
                    .getVisionTextDetector();        }

        public MLOcrDetector build() {
            zzad var1 = new zzad(this.zze, this.zzdg);
            return new MLOcrDetector(var1, mDetector /*, (zzb)null*/);
        }
    }


}
