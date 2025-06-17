@echo off
echo Compiling all Java files...

javac ^
    src\client\Client.java ^
    src\server\ClientHandler.java ^
    src\server\Server.java ^
    src\server\SocialGraphLoader.java ^
    src\server\UsersLoader.java ^
	src\common\Packet.java ^
	src\common\SenderState.java

IF %ERRORLEVEL% EQU 0 (
    echo Compilation successful!
) ELSE (
    echo Compilation failed! Check the errors above.
)

pause