package emu.grasscutter.auth;

import static emu.grasscutter.config.Configuration.ACCOUNT;
import static emu.grasscutter.config.Configuration.SERVER;
import static emu.grasscutter.utils.lang.Language.translate;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.AuthenticationSystem.AuthenticationRequest;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.dispatch.*;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.utils.*;
import io.javalin.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;

/** A class containing default authenticators. */
public final class DefaultAuthenticators {

    /** Handles the authentication request from the username and password form. */
    public static class PasswordAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            boolean useIntegrationPassword = ACCOUNT.useIntegrationPassword;
            var response = new LoginResultJson();

            var requestData = request.getPasswordRequest();
            assert requestData != null; // This should never be null.

            boolean successfulLogin = false;
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("request.getContext() == null");
                return null;
            }
            String address = Utils.address(request.getContext());

            String responseMessage = translate("messages.dispatch.account.username_error");
            String loggerMessage = "";

            if (useIntegrationPassword) {
                // 使用一体化密码
                requestData.parse();
            }

            // Get account from database.
            Account account = DatabaseHelper.getAccountByName(requestData.account);
            // Check if account exists.
            if (account == null && ACCOUNT.autoCreate && !useIntegrationPassword) {
                // This account has been created AUTOMATICALLY. There will be no permissions added.
                account = DatabaseHelper.createAccountWithUid(requestData.account, 0);

                // Check if the account was created successfully.
                if (account == null) {
                    responseMessage = translate("messages.dispatch.account.username_create_error");
                    Grasscutter.getLogger()
                            .info(translate("messages.dispatch.account.account_login_create_error", address));
                } else {
                    // Continue with login.
                    successfulLogin = true;

                    String uid = response.data.account.uid;
                    if (uid != null) {
                        // Log the creation.
                        Grasscutter.getLogger()
                            .info(
                                translate(
                                    "messages.dispatch.account.account_login_create_success",
                                    address,
                                    response.data.account.uid));
                    } else {
                        responseMessage = "账号登录失败, uid为空";
                        Grasscutter.getLogger().error("账号登录失败, uid为空");
                    }
                }
            } else if (account != null) {
                successfulLogin = true;
            } else
                loggerMessage = translate("messages.dispatch.account.account_login_exist_error", address);

            if (useIntegrationPassword) {
                // 一体化密码不正确
                if (account != null && !Objects.equals(account.getPassword(), requestData.password)) {
                    successfulLogin = false;
                    responseMessage = "密码不正确 请首先确认格式再确认密码正确性。\n例如: abc&&1234";
                }
            }

            // Set response data.
            if (successfulLogin) {
                response.message = "OK";
                response.data.account.uid = account.getId();
                response.data.account.token = account.generateSessionKey();
                response.data.account.email = account.getEmail();

                loggerMessage =
                        translate("messages.dispatch.account.login_success", address, account.getId());
            } else {
                response.retcode = -201;
                response.message = responseMessage;
            }
            Grasscutter.getLogger().info(loggerMessage);

            return response;
        }
    }

    public static class ExperimentalPasswordAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            LoginResultJson response = new LoginResultJson();

            var requestData = request.getPasswordRequest();
            assert requestData != null; // This should never be null.

            boolean successfulLogin = false;
            String address = null;
            if (request.getContext() != null) {
                address = Utils.address(request.getContext());
            }
            String responseMessage = translate("messages.dispatch.account.username_error");
            String loggerMessage = "";
            String decryptedPassword = "";
            try {
                byte[] key = FileUtils.readResource("/keys/auth_private-key.der");
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                RSAPrivateKey private_key = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

                cipher.init(Cipher.DECRYPT_MODE, private_key);

                decryptedPassword =
                        new String(
                                cipher.doFinal(Utils.base64Decode(request.getPasswordRequest().password)),
                                StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                if (requestData.is_crypto) {
                    response.retcode = -201;
                    response.message = "无法解密客户端的给定密码 请确认您使用了正确的游戏版本";
                    return response;
                } else {
                    decryptedPassword = request.getPasswordRequest().password;
                }
            }

            if (decryptedPassword == null) {
                successfulLogin = false;
                loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                responseMessage = translate("messages.dispatch.account.password_error");
            }

            // Get account from database.
            Account account = DatabaseHelper.getAccountByName(requestData.account);
            // Check if account exists.
            if (account == null && ACCOUNT.autoCreate) {
                // This account has been created AUTOMATICALLY. There will be no permissions added.
                if (decryptedPassword != null && decryptedPassword.length() >= 8) {
                    account = DatabaseHelper.createAccountWithUid(requestData.account, 0);

                    // Check if the account was created successfully.
                    if (account == null) {
                        responseMessage = translate("messages.dispatch.account.username_create_error");
                        loggerMessage =
                            translate("messages.dispatch.account.account_login_create_error", address);
                    } else {
                        account.setPassword(
                            BCrypt.withDefaults().hashToString(12, decryptedPassword.toCharArray()));
                        account.save();
                    }

                    // Check if the account was created successfully.
                    if (account != null) {
                        // Continue with login.
                        successfulLogin = true;

                        // Log the creation.
                        Grasscutter.getLogger()
                            .info(
                                translate(
                                    "messages.dispatch.account.account_login_create_success",
                                    address,
                                    response.data.account.uid));
                    }
                } else {
                    successfulLogin = false;
                    loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                    responseMessage = translate("messages.dispatch.account.password_length_error");
                }
            } else if (account != null) {
                if (account.getPassword() != null && !account.getPassword().isEmpty()) {
                    if (decryptedPassword != null && BCrypt.verifyer().verify(decryptedPassword.toCharArray(),
                        account.getPassword()).verified) {
                        successfulLogin = true;
                    }
                } else {
                    successfulLogin = false;
                    loggerMessage =
                            translate("messages.dispatch.account.login_password_storage_error", address);
                    responseMessage = translate("messages.dispatch.account.password_storage_error");
                }
            } else {
                loggerMessage = translate("messages.dispatch.account.account_login_exist_error", address);
            }

            // Set response data.
            if (successfulLogin) {
                response.message = "OK";
                response.data.account.uid = account.getId();
                response.data.account.token = account.generateSessionKey();
                response.data.account.email = account.getEmail();

                loggerMessage =
                        translate("messages.dispatch.account.login_success", address, account.getId());
            } else {
                response.retcode = -201;
                response.message = responseMessage;
            }
            Grasscutter.getLogger().info(loggerMessage);

            return response;
        }
    }

    /** Handles the authentication request from the game when using a registry token. */
    public static class TokenAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();

            var requestData = request.getTokenRequest();
            assert requestData != null;

            boolean successfulLogin;
            String loggerMessage;

            String address = null;
            if (request.getContext() != null)
                address = Utils.address(request.getContext());
            else
                Grasscutter.getLogger().error("TokenAuthenticator.authenticate -> request.getContext()==null");


            // Log the attempt.
            Grasscutter.getLogger()
                    .info(translate("messages.dispatch.account.login_token_attempt", address));

            // Get account from database.
            Account account = DatabaseHelper.getAccountById(requestData.uid);

            // Check if account exists/token is valid.
            successfulLogin = account != null && account.getSessionKey().equals(requestData.token);

            // Set response data.
            if (successfulLogin) {
                response.message = "OK";
                response.data.account.uid = account.getId();
                response.data.account.token = account.getSessionKey();
                response.data.account.email = account.getEmail();

                // Log the login.
                loggerMessage =
                        translate("messages.dispatch.account.login_token_success", address, requestData.uid);
            } else {
                response.retcode = -201;
                response.message = translate("messages.dispatch.account.account_cache_error");

                // Log the failure.
                loggerMessage = translate("messages.dispatch.account.login_token_error", address);
            }

            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    /** Handles the authentication request from the game when using a combo token/session key. */
    public static class SessionKeyAuthenticator implements Authenticator<ComboTokenResJson> {
        private static final int MAX_LOGIN_NUM_TIME = 1000;         // 登录并发检查间隔1秒
        private static final Queue<Long> connectionTimes = new LinkedList<>();  //服务器登录并发过高处理
        private static final Map<String, Queue<Long>> ipRequestTimes = new ConcurrentHashMap<>();//处理同一IP在限定时间内访问次数过多

        @Override
        public ComboTokenResJson authenticate(AuthenticationRequest request) {
            var response = new ComboTokenResJson();

            var requestData = request.getSessionKeyRequest();
            var loginData = request.getSessionKeyData();
            assert requestData != null;
            assert loginData != null;

            int successfulLoginRetcode;
            String loggerMessage;

            String address = null;
            if (request.getContext() != null)
                address = Utils.address(request.getContext());
            else
                Grasscutter.getLogger().error("SessionKeyAuthenticator.authenticate -> request.getContext()==null");

            // Get account from database.
            Account account = DatabaseHelper.getAccountById(loginData.uid);

            // Check if account exists/token is valid.
            if (account == null)
                successfulLoginRetcode = -1;
            else if (!account.getSessionKey().equals(loginData.token))
                successfulLoginRetcode = -2;
            else
                successfulLoginRetcode = 0;

            long currentTime = System.currentTimeMillis();

            /* 服务器登录并发过高处理 */
            synchronized (connectionTimes) {
                while (!connectionTimes.isEmpty() && (currentTime - connectionTimes.peek() > MAX_LOGIN_NUM_TIME)) {
                    connectionTimes.poll();
                }
                // 检查当前连接数量是否超过限制
                if ((connectionTimes.size() < ACCOUNT.loginMaxConnNum) || SERVER.isDevServer) { // 不要更改这里 应该是两个变量/常量比较
                    connectionTimes.add(currentTime);
                    Grasscutter.getLogger().debug("连接人数小于设定的值 玩家可以进入游戏");
                } else {
                    Grasscutter.getLogger().warn("登录并发数过高 连接人数大于设定的值:{} 玩家不可以进入游戏 请稍后再试", ACCOUNT.loginMaxConnNum);
                    response.retcode = -201;
                    response.message = "当前登录并发数过高 请稍后再试(点击右下角登录图标)";
                    return response;
                }
            }

            /* 处理同一IP在限定时间内访问次数过多 */
            ipRequestTimes.putIfAbsent(address, new LinkedList<>());
            synchronized (ipRequestTimes.get(address)) {
                Queue<Long> requestTimes = ipRequestTimes.get(address);
                while (!requestTimes.isEmpty() && (currentTime - requestTimes.peek() > ACCOUNT.ipBlackListTimeWindow)) {
                    requestTimes.poll();
                }
                // 检查同一IP的请求次数是否超过限制
                if ((requestTimes.size() < ACCOUNT.ipBlackListCount) || SERVER.isDevServer) {
                    requestTimes.add(currentTime);
                    Grasscutter.getLogger().debug("IP {} 的请求次数小于限制值", address);
                } else {
                    Grasscutter.getLogger().warn("IP {} 在时间窗口内请求次数过多 玩家不可以进入游戏 已暂时被服务器拉黑", address);
                    response.retcode = -202;
                    response.message = "您的IP请求次数过多 已暂时被服务器拉黑 请稍后再试";
                    return response;
                }
            }

            // Set response data.
            if (successfulLoginRetcode == 0) {
                response.message = "OK";
                response.data.open_id = account.getId();
                response.data.combo_id = "157795300";
                response.data.combo_token = account.generateLoginToken();

                // Log the login.
                loggerMessage = translate("messages.dispatch.account.combo_token_success", address);

            } else {
                // 会话密钥错误
                response.retcode = -201;
                response.message = successfulLoginRetcode + " 会话密钥错误";
                // Log the failure.
                loggerMessage = translate("messages.dispatch.account.combo_token_error", address);
            }

            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    /** Handles authentication requests from external sources. */
    public static class ExternalAuthentication implements ExternalAuthenticator {
        @Override
        public void handleLogin(AuthenticationRequest request) {
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("handleLogin 执行出错 getContext==null");
                return;
            }
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handleAccountCreation(AuthenticationRequest request) {
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("handleAccountCreation 执行出错 getContext==null");
                return;
            }
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handlePasswordReset(AuthenticationRequest request) {
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("handlePasswordReset 执行出错 getContext==null");
                return;
            }
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }
    }

    /** Handles authentication requests from OAuth sources.Zenlith */
    public static class OAuthAuthentication implements OAuthAuthenticator {
        @Override
        public void handleLogin(AuthenticationRequest request) {
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("-1 handleTokenProcess 执行出错 getContext==null");
                return;
            }
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handleRedirection(AuthenticationRequest request, ClientType type) {
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("-2 handleTokenProcess 执行出错 getContext==null");
                return;
            }
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handleTokenProcess(AuthenticationRequest request) {
            if (request.getContext() == null) {
                Grasscutter.getLogger().error("-3 handleTokenProcess 执行出错 getContext==null");
                return;
            }
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }
    }

    /** Validates a session token during game login. */
    public static class SessionTokenValidator implements Authenticator<Account> {
        @Override
        public Account authenticate(AuthenticationRequest request) {
            var tokenRequest = request.getTokenRequest();
            if (tokenRequest == null) {
                Grasscutter.getLogger().warn("Invalid session token validator request.");
                return null;
            }

            // Prepare the request.
            var client = Grasscutter.getGameServer().getDispatchClient();
            var future = new CompletableFuture<Account>();

            client.registerCallback(
                    PacketIds.TokenValidateRsp,
                    packet -> {
                        var data = IDispatcher.decode(packet);

                        // Check if the token is valid.
                        var valid = data.get("valid").getAsBoolean();
                        if (!valid) {
                            future.complete(null);
                            return;
                        }

                        // Return the account data.
                        future.complete(IDispatcher.decode(data.get("account"), Account.class));
                    });
            client.sendMessage(PacketIds.TokenValidateReq, tokenRequest);

            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** Handles authentication for the web GM Handbook. */
    public static class HandbookAuthentication implements HandbookAuthenticator {
        private final String authPage;

        public HandbookAuthentication() {
            try {
                this.authPage = new String(FileUtils.readResource("/html/handbook_auth.html"));
            } catch (Exception ignored) {
                throw new RuntimeException("Failed to load handbook auth page.");
            }
        }

        @Override
        public void presentPage(AuthenticationRequest request) {
            var ctx = request.getContext();
            if (ctx == null) return;

            // Check to see if an IP authentication can be performed.
            if (Grasscutter.getRunMode() == ServerRunMode.HYBRID) {
                var player = Grasscutter.getGameServer().getPlayerByIpAddress(Utils.address(ctx));
                if (player != null) {
                    // Get the player's session token.
                    var sessionKey = player.getAccount().getSessionKey();
                    // Respond with the handbook auth page.
                    ctx.status(200)
                            .result(
                                    this.authPage
                                            .replace("{{VALUE}}", "true")
                                            .replace("{{SESSION_TOKEN}}", sessionKey)
                                            .replace("{{PLAYER_ID}}", String.valueOf(player.getUid())));
                    return;
                }
            }

            // Respond with the handbook auth page.
            ctx.contentType(ContentType.TEXT_HTML).result(this.authPage);
        }

        @Override
        public Response authenticate(AuthenticationRequest request) {
            var ctx = request.getContext();
            if (ctx == null) return null;

            // Get the body data.
            var playerId = ctx.formParam("playerid");
            if (playerId == null) {
                return Response.builder().status(400).body("Invalid player ID.").build();
            }

            try {
                // Get the player's session token.
                var sessionKey = DispatchUtils.fetchSessionKey(Integer.parseInt(playerId));
                if (sessionKey == null) {
                    return Response.builder().status(400).body("Invalid player ID.").build();
                }

                // Check if the account is banned.
                return Response.builder()
                        .status(200)
                        .body(
                                this.authPage
                                        .replace("{{VALUE}}", "true")
                                        .replace("{{SESSION_TOKEN}}", sessionKey)
                                        .replace("{{PLAYER_ID}}", playerId))
                        .build();
            } catch (NumberFormatException ignored) {
                return Response.builder().status(500).body("Invalid player ID.").build();
            }
        }
    }
}
