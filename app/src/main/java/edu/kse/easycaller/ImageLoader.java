package edu.kse.easycaller;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImageLoader {

    private Uri imageUri;
    private Activity activity;

    // The request code used to start loading image activity.
    public static int IMAGE_REQUEST_CODE = 100;

    // The request code used to request permission to load images from external storage.
    public static final String READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;
    public static final int READ_EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE = 201;

    // The request code used to request permission to capture image from camera.
    public static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    public static final int CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE = 2011;

    // includeCamera if to include camera intents.
    private boolean includeCamera;

    // includeDocuments if to include KitKat documents activity containing all sources.
    private boolean includeDocuments;

    ImageLoader(@NonNull Activity activity){
        this.activity = activity;
    }

    public ImageLoader setRequestCode(int requestCode){
        IMAGE_REQUEST_CODE = requestCode;
        return this;
    }

    public ImageLoader includeCamera(boolean includeCamera){
        this.includeCamera = includeCamera;
        return this;
    }
    
    public ImageLoader includeDocuments(boolean includeDocuments){
        this.includeDocuments = includeDocuments;
        return this;
    }

    public void load() {

        if( includeCamera && isExplicitCameraPermissionRequired(activity) ) {
            // request permissions and handle the result in onRequestPermissionsResult()
            String[] permissions = {Manifest.permission.CAMERA};
            ActivityCompat.requestPermissions(activity, permissions, CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE);

        }
        if ( includeDocuments && isReadExternalStoragePermissionsRequired(activity, imageUri) ) {
            // request permissions and handle the result in onRequestPermissionsResult()
            String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions( activity, permissions, READ_EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
        }

        startLoading(activity);
    }

    /**
     * Check if explicitly requesting camera permission is required.<br>
     * It is required in Android Marshmallow and above if "CAMERA" permission is requested in the
     * manifest.<br>
     * See <a
     * href="http://stackoverflow.com/questions/32789027/android-m-camera-intent-permission-bug">StackOverflow
     * question</a>.
     */
    private static boolean isExplicitCameraPermissionRequired(@NonNull Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
               && hasPermissionInManifest(context, "android.permission.CAMERA")
               && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if the app requests a specific permission in the manifest.
     *
     * @param permissionName the permission to check
     * @return true - the permission in requested in manifest, false if not.
     */
    private static boolean hasPermissionInManifest(@NonNull Context context, @NonNull String permissionName) {
        String packageName = context.getPackageName();
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            final String[] declaredPermissions = packageInfo.requestedPermissions;
            if (declaredPermissions != null) {
                for (String p : declaredPermissions) {
                    if (p.equalsIgnoreCase(permissionName)) {
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    /**
     * Check if the given image URI requires READ_EXTERNAL_STORAGE permissions.<br>
     * Only relevant for API version 23 and above and not required for all URI's depends on the
     * implementation of the app that was used for loading the image. So we just test if we can open
     * the stream or do we get an exception when we try, Android is awesome.
     *
     * @param context used to access Android APIs, like content resolve, it is your activity/fragment/widget.
     * @param uri the result URI of image pick.
     * @return true - required permission are not granted, false - either no need for permissions or they are granted
     */
    private static boolean isReadExternalStoragePermissionsRequired(@NonNull Context context, @NonNull Uri uri) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && isUriRequiresPermissions(context, uri);
    }

    /**
     * Test if we can open the given Android URI to test if permission required error is thrown.<br>
     * Only relevant for API version 23 and above.
     *
     * @param context used to access Android APIs, like content resolve, it is your activity/fragment/widget.
     * @param uri the result URI of image pick.
     */
    private static boolean isUriRequiresPermissions(@NonNull Context context, @NonNull Uri uri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            InputStream stream = resolver.openInputStream(uri);
            if (stream != null) {
                stream.close();
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Start an activity to get image for cropping using chooser intent that will have all the
     * available applications for the device like camera (MyCamera), galery (Photos), store apps
     * (Dropbox), etc.<br>
     * Use "select_picture" string resource to set pick chooser title.
     *
     * @param activity the activity to be used to start activity from
     */
    private void startLoading(@NonNull Activity activity) {
        String title = activity.getString(R.string.select_picture);
        Intent intent = getPickImageChooserIntent(activity, title);
        activity.startActivityForResult(intent, IMAGE_REQUEST_CODE);
    }


    /**
     * Create a chooser intent to load the source to get image from.<br>
     * The source can be camera's (ACTION_IMAGE_CAPTURE) or gallery's (ACTION_GET_CONTENT).<br>
     * All possible sources are added to the intent chooser.<br>
     *
     * @param context used to access Android APIs, like content resolve, it is your
     *     activity/fragment/widget.
     * @param title the title to use for the chooser UI
     */
    private Intent getPickImageChooserIntent(@NonNull Context context, CharSequence title) {

        List<Intent> allIntents = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();

        // collect all camera intents if Camera permission is available
        if (includeCamera && !isExplicitCameraPermissionRequired(context)) {
            allIntents.addAll(getCameraIntents(context, packageManager));
        }

        List<Intent> galleryIntents = getGalleryIntents(packageManager, Intent.ACTION_GET_CONTENT, includeDocuments);
        if (galleryIntents.size() == 0) {
            // if no intents found for get-content try pick intent action (Huawei P9).
            galleryIntents = getGalleryIntents(packageManager, Intent.ACTION_PICK, includeDocuments);
        }
        allIntents.addAll(galleryIntents);

        Intent target;
        if (allIntents.isEmpty()) {
            target = new Intent();
        } else {
            target = allIntents.get(allIntents.size() - 1);
            allIntents.remove(allIntents.size() - 1);
        }

        // Create a chooser from the main  intent
        Intent chooserIntent = Intent.createChooser(target, title);

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        return chooserIntent;
    }

    /**
     *  Get all Camera intents for capturing image using device camera apps.
     */
    private static List<Intent> getCameraIntents(@NonNull Context context, @NonNull PackageManager packageManager) {

        List<Intent> allIntents = new ArrayList<>();

        // Determine Uri of camera image to  save.
        Uri outputFileUri = getCaptureImageOutputUri(context);

        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo res : listCam) {
            Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            if (outputFileUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            }
            allIntents.add(intent);
        }

        return allIntents;
    }

    /**
     * Get URI to image received from capture by camera.
     *
     * @param context used to access Android APIs, like content resolve, it is your
     *     activity/fragment/widget.
     */
    private static Uri getCaptureImageOutputUri(@NonNull Context context) {
        Uri outputFileUri = null;
        File imagePath = context.getExternalCacheDir();
        if (imagePath != null) {
            outputFileUri = Uri.fromFile(new File(imagePath.getPath(), "pickImageResult.jpeg"));
        }
        return outputFileUri;
    }

    /**
     * Get all Gallery intents for getting image from one of the apps of the device that handle
     * images.
     */
    private static List<Intent> getGalleryIntents(@NonNull PackageManager packageManager, String action, boolean includeDocuments) {
        List<Intent> intents = new ArrayList<>();
        Intent galleryIntent = action.equals(Intent.ACTION_GET_CONTENT) ?
                new Intent(action) : new Intent(action, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
        for (ResolveInfo res : listGallery) {
            Intent intent = new Intent(galleryIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            intents.add(intent);
        }

        // remove documents intent
        if (!includeDocuments) {
            for (Intent intent : intents) {
                if (Objects.requireNonNull(intent
                        .getComponent())
                        .getClassName()
                        .equals("com.android.documentsui.DocumentsActivity")) {
                    intents.remove(intent);
                    break;
                }
            }
        }
        return intents;
    }

    /**
     * Get the URI of the selected image from { @link #getPickImageChooserIntent(Context)}.<br>
     * Will return the correct URI for camera and gallery image.
     *
     * @param data the returned data of the activity result
     */
    public Uri getImageResultUri(@Nullable Intent data) {
        boolean isCamera = true;
        if (data != null && data.getData() != null) {
            String action = data.getAction();
            isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
        }
        return isCamera || data.getData() == null ? getCaptureImageOutputUri(activity) : getRealUri(activity, data.getData());
    }

    private static Uri getRealUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            assert cursor != null;
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return Uri.fromFile(new File(cursor.getString(column_index)));
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }
}
