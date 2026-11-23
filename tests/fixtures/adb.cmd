@echo off
if not "%~1"=="-s" exit /b 90
if not "%~2"=="39f26a79" exit /b 91
>> "%CONTOUR_INSTALL_TEST_LOG%" echo %*
if "%~3"=="start-server" (
    echo * daemon not running; starting now at tcp:5037 1>&2
    echo * daemon started successfully 1>&2
    exit /b 0
)
if "%~3"=="get-state" (
    if "%CONTOUR_INSTALL_TEST_CASE%"=="unauthorized" (
        echo unauthorized
    ) else (
        echo device
    )
    exit /b 0
)
if "%~3"=="shell" (
    if "%~4"=="getprop" (
        if "%~5"=="ro.product.device" (
            if "%CONTOUR_INSTALL_TEST_CASE%"=="wrong-device" (
                echo another-phone
            ) else (
                echo fajita
            )
            exit /b 0
        )
        if "%~5"=="ro.product.model" (
            echo ONEPLUS A6013
            exit /b 0
        )
        if "%~5"=="ro.build.version.sdk" (
            echo 35
            exit /b 0
        )
    )
    if "%~4"=="pm" (
        echo package:/data/app/test-contour/base.apk
        exit /b 0
    )
    if "%~4"=="am" (
        echo Starting: Intent { cmp=app.contouros.home/.HomeActivity }
        exit /b 0
    )
)
if "%~3"=="install" (
    if "%CONTOUR_INSTALL_TEST_CASE%"=="install-error" (
        echo Failure [INSTALL_FAILED_TEST] 1>&2
        rem Also reject a failure response if a wrapper reports exit code zero.
        exit /b 0
    )
    echo Performing Streamed Install
    echo Success
    exit /b 0
)
exit /b 92
