package edu.kse.easycaller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.arch.persistence.room.Room;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ImageView;
import android.widget.Toast;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {

    private static final int USER_PICTURE_REQUEST_CODE = 2;
    private static final String USERS_TABLE_NAME = "Users.db";

    private final ImageLoader imageLoader;

    CircleImageView dialogPhotoIV;
    AppCompatTextView dialogAddPhotoTV;
    List<AppCompatEditText> dialogPhoneNumberETs = new LinkedList<>();

    private String firstName;
    private String lastName;
    private String company;
    private String profilePictureUri;
    private String phoneNumber;

    private volatile static User currentUser;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    PagerAdapter pagerAdapter;

    //The {@link ViewPager} that will host the section contents.
    ViewPager viewPager;

    private static UserDao userDao;

    public MainActivity(){
        // Initialize image loader
        imageLoader = new ImageLoader(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize user data access object (userDao)
        userDao = Room.databaseBuilder(this, AppDatabase.class, USERS_TABLE_NAME)
                      .allowMainThreadQueries()
                      .build()
                      .userDao();

        // Create adapter that will return a fragment for each of user.
        pagerAdapter = new MyPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        viewPager = findViewById(R.id.container);
        viewPager.setAdapter(pagerAdapter);
    }

    public void onAnswerBtnClicked(View view) {
        if (isPermissionGranted()) {
            startCalling();
        }
    }

    @SuppressLint("MissingPermission")
    public void startCalling() {
        if(currentUser != null) {
            String phoneNumber = currentUser.getPhoneNumber();
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(callIntent);
            }
        }
    }

    public  boolean isPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Log.v("TAG","Permission is granted");
                return true;
            } else {
                Log.v("TAG","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v("TAG","Permission is granted");
            return true;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCalling();
                }
                break;
            }
        }
    }

    public void onAddUserBtnClicked(final View view) {

        final AppCompatDialog dialog = new AppCompatDialog(this, R.style.AppTheme_NoActionBar);
        dialog.setContentView(R.layout.fragment_add_new_contact);

        AppCompatButton cancelBtn = dialog.findViewById(R.id.cancelBtn);
        AppCompatButton doneBtn = dialog.findViewById(R.id.doneBtn);
        dialogPhotoIV = dialog.findViewById(R.id.photoIV);
        dialogAddPhotoTV = dialog.findViewById(R.id.addPhotoTV);
        AppCompatButton addPhoneBtn = dialog.findViewById(R.id.addPhoneBtn);
        final LinearLayoutCompat layout = dialog.findViewById(R.id.phoneLayout);

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppCompatEditText firstNameET = dialog.findViewById(R.id.firstNameET);
                AppCompatEditText lastNameET = dialog.findViewById(R.id.lastNameET);
                AppCompatEditText companyET = dialog.findViewById(R.id.company);

                firstName = firstNameET.getText().toString();
                lastName = lastNameET.getText().toString();
                company = companyET.getText().toString();
                for(AppCompatEditText editText : dialogPhoneNumberETs){
                    phoneNumber = editText.getText().toString();
                }

                dialog.cancel();

                final User user = new User();

                user.setFirstName(firstName);
                user.setLastName(lastName);
                user.setPhoneNumber(phoneNumber);
                user.setImageUri(profilePictureUri);
                user.setCompany(company);

                userDao.insertAll(user);

                pagerAdapter.notifyDataSetChanged();
            }
        });

        dialogPhotoIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageLoader.includeCamera(true)
                      .includeDocuments(true)
                      .setRequestCode(USER_PICTURE_REQUEST_CODE)
                      .load();
            }
        });

        addPhoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addPhoneNumber(layout);
            }
        });

        addPhoneNumber(layout);
        dialog.show();
    }

    private void addPhoneNumber(final LinearLayoutCompat layout){
        final View view = getLayoutInflater().inflate(R.layout.fragment_add_new_phone, layout, false);
        int index = layout.getChildCount() - 2;
        if(index < 0){
            index = 0;
        }

        AppCompatImageView showDeleteBtn = view.findViewById(R.id.showDeleteBtn);
        final AppCompatButton deleteBtn = view.findViewById(R.id.deleteBtn);

        showDeleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(deleteBtn.getVisibility() == View.VISIBLE){
                    deleteBtn.setVisibility(View.GONE);
                }else {
                    deleteBtn.setVisibility(View.VISIBLE);
                }
            }
        });

        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewGroup container = (ViewGroup) v.getParent();
                AppCompatEditText et = container.findViewById(R.id.phoneNumberET);

                dialogPhoneNumberETs.remove(et);
                layout.removeViewInLayout(container);

                layout.getChildAt(0).setVisibility(View.GONE);
                layout.getChildAt(0).setVisibility(View.VISIBLE);
            }
        });

        layout.addView(view, index);
        dialogPhoneNumberETs.add((AppCompatEditText) view.findViewById(R.id.phoneNumberET));
    }

    @Override
    public File getDataDir() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return super.getDataDir();
        }

        PackageManager m = getPackageManager();
        String dataDir = getPackageName();
        try {
            PackageInfo p = m.getPackageInfo(dataDir, 0);
            dataDir = p.applicationInfo.dataDir;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("TAG", "Error Package name not found ", e);
        }

        return new File(dataDir);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == USER_PICTURE_REQUEST_CODE){
            if(resultCode == RESULT_OK){
                Uri srcUri = imageLoader.getImageResultUri(data);

                String dirPath = getFilesDir().getAbsolutePath() + File.separator + "pictures";
                File picturesDir = new File(dirPath);
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs();
                }

                int userId = userDao.getCount() + 1;
                String pictureFileName = "user" + userId + ".jpg";

                File pictureFile = new File(dirPath, pictureFileName);
                try {
                    pictureFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Uri dstUri = Uri.parse( pictureFile.getAbsolutePath() );
                UCrop.of(srcUri, dstUri).withAspectRatio(1, 1).start(this);
            }
        }

        if (requestCode == UCrop.REQUEST_CROP) {
            if(resultCode == RESULT_OK) {
                final Uri resultUri = UCrop.getOutput(data);

                profilePictureUri = resultUri.toString();
                dialogPhotoIV.setImageURI(resultUri);

                Toast.makeText(this, profilePictureUri, Toast.LENGTH_LONG).show();

                dialogAddPhotoTV.setVisibility(View.INVISIBLE);
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            cropError.printStackTrace();
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class MyFragment extends Fragment {

        /**
         * The fragment argument representing the section number for this fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section number.
         */
        public static MyFragment newInstance(int sectionNumber) {
            MyFragment fragment = new MyFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            ImageView imageView = rootView.findViewById(R.id.appCompatImageView);

            int userId = getArguments().getInt(ARG_SECTION_NUMBER);

            try {
                currentUser = userDao.getById(userId);
                Uri uri = Uri.parse(currentUser.getImageUri());
                imageView.setImageURI(uri);
            }catch (Exception e){
                Log.e("Error: ", e.toString());
            }
            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public static class MyPagerAdapter extends FragmentPagerAdapter {

        private int count;

        MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a MyFragment (defined as a static inner class below).
            return MyFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            int currentCount = userDao.getCount();
            if(count != currentCount){
                Log.i("COUNT", currentCount + " :: " + count);
                count = currentCount;
                notifyDataSetChanged();
            }

            return count;
        }
    }
}
