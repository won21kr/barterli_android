/*******************************************************************************
 * Copyright 2014, barter.li
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package li.barter.fragments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.Request;
import com.android.volley.Request.Method;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import li.barter.R;
import li.barter.activities.AbstractBarterLiActivity;
import li.barter.activities.ScanIsbnActivity;
import li.barter.adapters.CropOptionAdapter;
import li.barter.data.DBInterface;
import li.barter.data.DatabaseColumns;
import li.barter.data.SQLConstants;
import li.barter.data.TableLocations;
import li.barter.data.DBInterface.AsyncDbQueryCallback;
import li.barter.http.BlMultiPartRequest;
import li.barter.http.BlRequest;
import li.barter.http.HttpConstants;
import li.barter.http.IBlRequestContract;
import li.barter.http.ResponseInfo;
import li.barter.http.HttpConstants.ApiEndpoints;
import li.barter.http.HttpConstants.RequestId;
import li.barter.models.CropOption;
import li.barter.utils.Logger;
import li.barter.utils.PhotoUtils;
import li.barter.utils.SharedPreferenceHelper;
import li.barter.utils.AppConstants.FragmentTags;
import li.barter.utils.AppConstants.QueryTokens;
import li.barter.utils.AppConstants.RequestCodes;

/**
 * @author Sharath Pandeshwar
 */

@FragmentTransition(enterAnimation = R.anim.slide_in_from_right, exitAnimation = R.anim.zoom_out, popEnterAnimation = R.anim.zoom_in, popExitAnimation = R.anim.slide_out_to_right)
public class EditProfileFragment extends AbstractBarterLiFragment implements
                OnClickListener, AsyncDbQueryCallback {

    private static final String TAG                     = "EditProfileFragment";

    private TextView            mFirstNameTextView;
    private TextView            mLastNameTextView;
    private TextView            mAboutMeTextView;
    private TextView            mPreferredLocationTextView;
    private ImageView           mProfileImageView;
    private ImageView           mEditPreferredLocationImageView;
    private boolean             mWasProfileImageChanged = false;
    private Uri                 mImageCaptureUri;
    private Bitmap              mCompressedPhoto;
    private File                mAvatarfile;
    // private Boolean mHasAboutMeDescriptionChanged = false;

    private static final int    PICK_FROM_CAMERA        = 1;
    private static final int    CROP_FROM_CAMERA        = 2;
    private static final int    PICK_FROM_FILE          = 3;

    @Override
    public View onCreateView(final LayoutInflater inflater,
                    final ViewGroup container, final Bundle savedInstanceState) {
        init(container);
        setHasOptionsMenu(true);
        final View view = inflater
                        .inflate(R.layout.fragment_profile_edit, null);

        mFirstNameTextView = (TextView) view
                        .findViewById(R.id.profile_name_first_name);
        mLastNameTextView = (TextView) view
                        .findViewById(R.id.profile_name_last_name);
        mAboutMeTextView = (TextView) view.findViewById(R.id.about_me);
        mPreferredLocationTextView = (TextView) view
                        .findViewById(R.id.current_location_text_in_edit_page);
        mProfileImageView = (ImageView) view
                        .findViewById(R.id.profile_pic_thumbnail);
        mEditPreferredLocationImageView = (ImageView) view
                        .findViewById(R.id.edit_current_location_button_in_edit_page);

        mProfileImageView.setOnClickListener(this);
        mEditPreferredLocationImageView.setOnClickListener(this);
        mAvatarfile = new File(Environment.getExternalStorageDirectory(), "barterli_avatar_small.png");

        if (SharedPreferenceHelper
                        .getBoolean(getActivity(), R.string.pref_is_about_me_description_set)) {
            mAboutMeTextView.setText(SharedPreferenceHelper
                            .getString(getActivity(), R.string.pref_is_about_me_description_set));
        }

        if (SharedPreferenceHelper
                        .getBoolean(getActivity(), R.string.pref_is_profile_pic_set)) {

            if (mAvatarfile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(mAvatarfile
                                .getAbsolutePath());
                mProfileImageView.setImageBitmap(bmp);
            }
        }

        if (SharedPreferenceHelper
                        .contains(getActivity(), R.string.pref_profile_first_name)) {
            mFirstNameTextView
                            .setText(SharedPreferenceHelper
                                            .getString(getActivity(), R.string.pref_profile_first_name));
        }

        if (SharedPreferenceHelper
                        .contains(getActivity(), R.string.pref_profile_last_name)) {
            mLastNameTextView
                            .setText(SharedPreferenceHelper
                                            .getString(getActivity(), R.string.pref_profile_last_name));
        }

        if (SharedPreferenceHelper
                        .contains(getActivity(), R.string.pref_profile_about_me_description)) {
            mAboutMeTextView.setText(SharedPreferenceHelper
                            .getString(getActivity(), R.string.pref_profile_about_me_description));
        }

        if (SharedPreferenceHelper
                        .contains(getActivity(), R.string.pref_location)) {
            loadPreferredLocation();
        }

        setActionBarDrawerToggleEnabled(false);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.menu_profile_edit, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home: {
                onUpNavigate();
                return true;
            }

            case R.id.action_profile_save: {

                String mFirstName = mFirstNameTextView.getText().toString();
                String mLastName = mLastNameTextView.getText().toString();
                String mAboutMe = mAboutMeTextView.getText().toString();

                //TODO move saving to shared preference to network success listener
                SharedPreferenceHelper
                                .set(getActivity(), R.string.pref_profile_first_name, mFirstName);

                SharedPreferenceHelper
                                .set(getActivity(), R.string.pref_profile_last_name, mLastName);

                SharedPreferenceHelper
                                .set(getActivity(), R.string.pref_profile_about_me_description, mAboutMe);

                saveProfileInfoToServer(mFirstName, mLastName, mAboutMe, mWasProfileImageChanged);

                // showToast("Saved", false);
                return true;
            }

            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void loadPreferredLocation() {

        DBInterface.queryAsync(QueryTokens.LOAD_LOCATION_FROM_PROFILE_EDIT_PAGE, null, false, TableLocations.NAME, null, DatabaseColumns.LOCATION_ID
                        + SQLConstants.EQUALS_ARG, new String[] {
            SharedPreferenceHelper
                            .getString(getActivity(), R.string.pref_location)
        }, null, null, null, null, this);
    }

    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == QueryTokens.LOAD_LOCATION_FROM_PROFILE_EDIT_PAGE) {

            if (cursor.moveToFirst()) {
                String mPrefAddressName = cursor.getString(cursor
                                .getColumnIndex(DatabaseColumns.NAME))
                                + ", "
                                + cursor.getString(cursor
                                                .getColumnIndex(DatabaseColumns.ADDRESS));

                mPreferredLocationTextView.setText(mPrefAddressName);
            }

            cursor.close();

        }
    }

    @Override
    public void onStop() {
        super.onStop();
        DBInterface.cancelAsyncQuery(QueryTokens.LOAD_LOCATION_FROM_PROFILE_EDIT_PAGE);
    }

    /*
     * (non-Javadoc)
     * @see li.barter.fragments.AbstractBarterLiFragment#getVolleyTag()
     */
    @Override
    protected Object getVolleyTag() {
        return TAG;
    }

    /*
     * (non-Javadoc)
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.edit_current_location_button_in_edit_page: {
                loadFragment(mContainerViewId, (AbstractBarterLiFragment) Fragment
                                .instantiate(getActivity(), SelectPreferredLocationFragment.class
                                                .getName(), null), FragmentTags.SELECT_PREFERRED_LOCATION_FROM_PROFILE, true, FragmentTags.PROFILE);

                break;
            }

            case R.id.profile_pic_thumbnail: {
                editSetProfilePictureDialog();
                break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK)
            return;

        switch (requestCode) {
            case PICK_FROM_CAMERA:
                //doCrop(PICK_FROM_CAMERA);
                setAndSaveImage(mImageCaptureUri, PICK_FROM_CAMERA);
                break;

            case PICK_FROM_FILE:
                mImageCaptureUri = data.getData();
                setAndSaveImage(mImageCaptureUri, PICK_FROM_FILE);
                //doCrop(PICK_FROM_FILE);
                break;

            case CROP_FROM_CAMERA:
                Bundle extras = data.getExtras();
                if (extras != null) {
                    mCompressedPhoto = extras.getParcelable("data");
                    mProfileImageView.setImageBitmap(mCompressedPhoto);
                }
                PhotoUtils.saveImage(mCompressedPhoto, "barterli_avatar_small.png");
                SharedPreferenceHelper
                                .set(getActivity(), R.string.pref_is_profile_pic_set, true);
                break;

        }
    } // End of onActivityResult

    /**
     * Method to handle click on profile image
     */
    private void editSetProfilePictureDialog() {
        final String[] items = new String[] {
                "From Camera", "From Gallery"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.select_dialog_item, items);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {

                if (item == 0) { // Pick from camera
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    File mProfileFile = new File(android.os.Environment
                                    .getExternalStorageDirectory(), "barterli_avatar.jpg");

                    mImageCaptureUri = Uri.fromFile(mProfileFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri);

                    try {
                        intent.putExtra("return-data", true);
                        startActivityForResult(intent, PICK_FROM_CAMERA);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                    }

                } else { // pick from file
                    Intent intent = new Intent();
                    intent.setType("image/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent
                                    .createChooser(intent, "Complete Action Using"), PICK_FROM_FILE);
                }
            }
        });

        final AlertDialog dialog = builder.create();
        dialog.show();
    } // End of editSetProfilePictureDialog

    /*
     * private void doCrop(final int source_of_image) { Log.v("DO-CROP",
     * mImageCaptureUri.toString()); final ArrayList<CropOption> cropOptions =
     * new ArrayList<CropOption>(); String source_string; if (source_of_image ==
     * PICK_FROM_FILE) { source_string = "Gallery"; } else { source_string =
     * "Camera"; } final String source = source_string; Intent intent = new
     * Intent("com.android.camera.action.CROP"); intent.setType("image/*");
     * List<ResolveInfo> list = getActivity().getPackageManager()
     * .queryIntentActivities(intent, 0); int size = list.size(); if (size == 0)
     * { showToast("Could not find an App to Crop Image", false);
     * mCompressedPhoto = PhotoUtils
     * .rotateBitmapIfNeededAndCompressIfTold(getActivity(), mImageCaptureUri,
     * source, true); if (mCompressedPhoto != null) {
     * mProfileImageView.setImageBitmap(mCompressedPhoto);
     * PhotoUtils.saveImage(mCompressedPhoto, "barterli_avatar_small.png");
     * SharedPreferenceHelper .set(getActivity(),
     * R.string.pref_is_profile_pic_set, true); } return; } else {
     * intent.setData(mImageCaptureUri); intent.putExtra("outputX", 150);
     * intent.putExtra("outputY", 150); intent.putExtra("aspectX", 1);
     * intent.putExtra("aspectY", 1); intent.putExtra("scale", true);
     * intent.putExtra("return-data", true); if (size == 1) { Intent i = new
     * Intent(intent); ResolveInfo res = list.get(0); i.setComponent(new
     * ComponentName(res.activityInfo.packageName, res.activityInfo.name));
     * startActivityForResult(i, CROP_FROM_CAMERA); } else { for (ResolveInfo
     * res : list) { final CropOption co = new CropOption(); co.title =
     * getActivity() .getPackageManager()
     * .getApplicationLabel(res.activityInfo.applicationInfo); co.icon =
     * getActivity() .getPackageManager()
     * .getApplicationIcon(res.activityInfo.applicationInfo); co.appIntent = new
     * Intent(intent); co.appIntent.setComponent(new
     * ComponentName(res.activityInfo.packageName, res.activityInfo.name));
     * cropOptions.add(co); } CropOptionAdapter adapter = new
     * CropOptionAdapter(getActivity(), cropOptions); AlertDialog.Builder
     * builder = new AlertDialog.Builder(getActivity());
     * builder.setTitle("Choose an Application to Crop Image");
     * builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
     * public void onClick(DialogInterface dialog, int item) {
     * startActivityForResult(cropOptions.get(item).appIntent,
     * CROP_FROM_CAMERA); } }); builder.setOnCancelListener(new
     * DialogInterface.OnCancelListener() {
     * @Override public void onCancel(DialogInterface dialog) { mCompressedPhoto
     * = PhotoUtils .rotateBitmapIfNeededAndCompressIfTold(getActivity(),
     * mImageCaptureUri, source, true); if (mCompressedPhoto != null) {
     * mProfileImageView.setImageBitmap(mCompressedPhoto);
     * PhotoUtils.saveImage(mCompressedPhoto, "barterli_avatar_small.png");
     * SharedPreferenceHelper .set(getActivity(),
     * R.string.pref_is_profile_pic_set, true); } } }); AlertDialog alert =
     * builder.create(); alert.show(); } } } // End of doCrop
     */

    private void setAndSaveImage(final Uri uri, final int source_of_image) {
        String source_string;
        if (source_of_image == PICK_FROM_FILE) {
            source_string = "Gallery";
        } else {
            source_string = "Camera";
        }

        //final String source = source_string;

        mCompressedPhoto = PhotoUtils
                        .rotateBitmapIfNeededAndCompressIfTold(getActivity(), uri, source_string, true);

        if (mCompressedPhoto != null) {
            mProfileImageView.setImageBitmap(mCompressedPhoto);
            PhotoUtils.saveImage(mCompressedPhoto, "barterli_avatar_small.png");
            SharedPreferenceHelper
                            .set(getActivity(), R.string.pref_is_profile_pic_set, true);
        }
        mWasProfileImageChanged = true;
    }

    private void saveProfileInfoToServer(final String firstName,
                    final String lastName, final String aboutMeDescription,
                    final Boolean shouldIncludePic) {

        // String url = HttpConstants.getApiBaseUrl()
        //+ ApiEndpoints.UPDATE_USER_INFO;
        String url = "http://162.243.198.171/api/v1/user_update.json";

        JSONObject mUserProfileObject = new JSONObject();
        JSONObject mUserProfileMasterObject = new JSONObject();
        try {
            mUserProfileObject.put(HttpConstants.FIRST_NAME, firstName);
            mUserProfileObject.put(HttpConstants.LAST_NAME, lastName);
            mUserProfileObject
                            .put(HttpConstants.DESCRIPTION, aboutMeDescription);
            mUserProfileMasterObject.put("user", mUserProfileObject);

            BlMultiPartRequest updateUserProfileRequest = new BlMultiPartRequest(Method.PUT, url, null, mVolleyCallbacks);

            updateUserProfileRequest
                            .addMultipartParam("user", "application/json", mUserProfileMasterObject
                                            .toString());
            String mProfilePicPath = mAvatarfile.getAbsolutePath();
            updateUserProfileRequest
                            .addFile(HttpConstants.PROFILE_PIC, mProfilePicPath);
            updateUserProfileRequest.setRequestId(RequestId.SAVE_USER_PROFILE);
            addRequestToQueue(updateUserProfileRequest, true, 0);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onSuccess(int requestId, IBlRequestContract request,
                    ResponseInfo response) {
        Log.v(TAG, response.toString());
        //TODO roll back to showProfile Activity
        if(requestId == RequestId.SAVE_USER_PROFILE){
            onUpNavigate();
        }
        

    }

    @Override
    public void onBadRequestError(int requestId, IBlRequestContract request,
                    int errorCode, String errorMessage,
                    Bundle errorResponseBundle) {
        Log.v(TAG, "Volley error");

    }

    @Override
    public void onInsertComplete(int token, Object cookie, long insertRowId) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDeleteComplete(int token, Object cookie, int deleteCount) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onUpdateComplete(int token, Object cookie, int updateCount) {
        // TODO Auto-generated method stub

    }

}
