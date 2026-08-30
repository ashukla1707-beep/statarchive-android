private void downloadAppUpdate(
        String apkUrl
) {

    if (
            apkUrl == null ||
            apkUrl.trim().isEmpty()
    ) {

        Toast.makeText(
                this,
                "Update download address is missing.",
                Toast.LENGTH_LONG
        ).show();

        return;
    }


    /*
     * Only allow HTTPS update downloads.
     */
    if (
            !apkUrl
                    .toLowerCase()
                    .startsWith(
                            "https://"
                    )
    ) {

        Toast.makeText(
                this,
                "Invalid update download address.",
                Toast.LENGTH_LONG
        ).show();

        return;
    }


    Toast.makeText(
            this,
            "Downloading update…",
            Toast.LENGTH_SHORT
    ).show();


    new Thread(
            () -> {

                HttpURLConnection connection =
                        null;


                File temporaryFile =
                        null;


                try {

                    URL url =
                            new URL(
                                    apkUrl
                            );


                    connection =
                            (HttpURLConnection)
                                    url.openConnection();


                    connection.setRequestMethod(
                            "GET"
                    );


                    connection.setConnectTimeout(
                            15000
                    );


                    connection.setReadTimeout(
                            30000
                    );


                    connection.setInstanceFollowRedirects(
                            true
                    );


                    connection.setUseCaches(
                            false
                    );


                    connection.setRequestProperty(
                            "Cache-Control",
                            "no-cache"
                    );


                    int responseCode =
                            connection.getResponseCode();


                    if (
                            responseCode < 200 ||
                            responseCode >= 300
                    ) {

                        throw new IOException(
                                "APK download failed."
                        );
                    }


                    File updatesDirectory =
                            new File(
                                    getCacheDir(),
                                    "updates"
                            );


                    if (
                            !updatesDirectory.exists() &&
                            !updatesDirectory.mkdirs()
                    ) {

                        throw new IOException(
                                "Couldn't create update directory."
                        );
                    }


                    temporaryFile =
                            new File(
                                    updatesDirectory,
                                    "stat-archive-update.download"
                            );


                    File finalApk =
                            new File(
                                    updatesDirectory,
                                    "stat-archive-update.apk"
                            );


                    if (
                            temporaryFile.exists()
                    ) {

                        temporaryFile.delete();
                    }


                    if (
                            finalApk.exists()
                    ) {

                        finalApk.delete();
                    }


                    try (
                            InputStream input =
                                    new BufferedInputStream(
                                            connection.getInputStream()
                                    );

                            FileOutputStream output =
                                    new FileOutputStream(
                                            temporaryFile
                                    )
                    ) {

                        byte[] buffer =
                                new byte[8192];


                        int count;


                        while (
                                (count =
                                        input.read(
                                                buffer
                                        )) != -1
                        ) {

                            output.write(
                                    buffer,
                                    0,
                                    count
                            );
                        }


                        output.flush();
                    }


                    /*
                     * Reject obviously invalid
                     * tiny downloads.
                     */
                    if (
                            temporaryFile.length() <
                                    50_000
                    ) {

                        throw new IOException(
                                "Downloaded update is invalid."
                        );
                    }


                    if (
                            !temporaryFile.renameTo(
                                    finalApk
                            )
                    ) {

                        copyFile(
                                temporaryFile,
                                finalApk
                        );


                        temporaryFile.delete();
                    }


                    /*
                     * IMPORTANT:
                     *
                     * Do NOT set pendingUpdateApk here.
                     *
                     * beginUpdateInstallation() will set it
                     * only if Android needs us to leave the app
                     * and open "Allow from this source" settings.
                     */
                    runOnUiThread(
                            () ->
                                    beginUpdateInstallation(
                                            finalApk
                                    )
                    );


                } catch (
                        Exception e
                ) {

                    if (
                            temporaryFile != null &&
                            temporaryFile.exists()
                    ) {

                        temporaryFile.delete();
                    }


                    runOnUiThread(
                            () ->
                                    Toast.makeText(
                                            MainActivity.this,
                                            "Couldn't download the update.",
                                            Toast.LENGTH_LONG
                                    ).show()
                    );


                } finally {

                    if (
                            connection != null
                    ) {

                        connection.disconnect();
                    }
                }
            }
    ).start();
}


/* =========================================================
   INSTALL DOWNLOADED APK
   ========================================================= */

private void beginUpdateInstallation(
        File apkFile
) {

    if (
            apkFile == null ||
            !apkFile.exists()
    ) {

        pendingUpdateApk =
                null;


        Toast.makeText(
                this,
                "Update file could not be found.",
                Toast.LENGTH_LONG
        ).show();


        return;
    }


    /*
     * Android 8+ requires the user to allow
     * this app to install unknown apps.
     *
     * pendingUpdateApk is used ONLY while
     * waiting for the user to return from
     * Android Settings.
     */
    if (
            Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O &&
            !getPackageManager()
                    .canRequestPackageInstalls()
    ) {

        pendingUpdateApk =
                apkFile;


        new AlertDialog.Builder(
                this
        )

                .setTitle(
                        "Allow app updates"
                )

                .setMessage(
                        "Android needs permission for Stat Archive to install its downloaded update. Enable \"Allow from this source\", then return to Stat Archive."
                )

                .setPositiveButton(
                        "Open settings",
                        (d, which) -> {

                            try {

                                Intent intent =
                                        new Intent(
                                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                                        );


                                intent.setData(
                                        Uri.parse(
                                                "package:" +
                                                        getPackageName()
                                        )
                                );


                                startActivity(
                                        intent
                                );


                            } catch (
                                    Exception e
                            ) {

                                pendingUpdateApk =
                                        null;


                                Toast.makeText(
                                        MainActivity.this,
                                        "Couldn't open installation settings.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                )

                .setNegativeButton(
                        "Cancel",
                        (d, which) ->
                                pendingUpdateApk =
                                        null
                )

                .setOnCancelListener(
                        d ->
                                pendingUpdateApk =
                                        null
                )

                .show();


        return;
    }


    /*
     * Android already allows Stat Archive
     * to install APK updates.
     *
     * Clear pendingUpdateApk BEFORE opening
     * the installer.
     *
     * This is the important fix that prevents
     * onResume() from opening the installer again.
     */
    pendingUpdateApk =
            null;


    installDownloadedApk(
            apkFile
    );
}


/* =========================================================
   OPEN ANDROID PACKAGE INSTALLER
   ========================================================= */

private void installDownloadedApk(
        File apkFile
) {

    if (
            apkFile == null ||
            !apkFile.exists()
    ) {

        pendingUpdateApk =
                null;


        Toast.makeText(
                this,
                "Update file could not be found.",
                Toast.LENGTH_LONG
        ).show();


        return;
    }


    try {

        Uri apkUri =
                FileProvider.getUriForFile(
                        this,
                        getPackageName()
                                + ".fileprovider",
                        apkFile
                );


        Intent intent =
                new Intent(
                        Intent.ACTION_VIEW
                );


        intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
        );


        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );


        /*
         * We are launching from an Activity,
         * so FLAG_ACTIVITY_NEW_TASK is not needed.
         *
         * Removing it gives the update installer
         * a cleaner Activity lifecycle and avoids
         * unnecessary task switching.
         */
        startActivity(
                intent
        );


    } catch (
            Exception e
    ) {

        pendingUpdateApk =
                null;


        Toast.makeText(
                this,
                "Couldn't start the Android update installer.",
                Toast.LENGTH_LONG
        ).show();
    }
}


/* =========================================================
   AFTER RETURNING FROM INSTALL-PERMISSION SETTINGS
   ========================================================= */

@Override
protected void onResume() {

    super.onResume();


    /*
     * Normally this is null.
     *
     * It is non-null ONLY when the user was sent
     * to Android's "Allow from this source" page.
     */
    if (
            pendingUpdateApk == null
    ) {

        return;
    }


    if (
            !pendingUpdateApk.exists()
    ) {

        pendingUpdateApk =
                null;

        return;
    }


    /*
     * Still waiting for installation permission.
     */
    if (
            Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O &&
            !getPackageManager()
                    .canRequestPackageInstalls()
    ) {

        return;
    }


    /*
     * Permission has now been granted.
     *
     * Copy the reference and CLEAR it first.
     * This guarantees that another onResume()
     * cannot reopen the installer.
     */
    File apk =
            pendingUpdateApk;


    pendingUpdateApk =
            null;


    installDownloadedApk(
            apk
    );
}
