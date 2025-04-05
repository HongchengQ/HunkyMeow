package emu.grasscutter.server.http.handlers;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.Utils;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class RegisterHandler {
    public record Result(String success, String message) {}
    public static void handleRegistration(@NotNull Context ctx) {
        Result result = checkRegister(ctx);

        // 设置响应的Content-Type为text/html
        ctx.contentType(ContentType.TEXT_HTML);

        String htmlFilePath = System.getProperty("user.dir") + "/account/result.html"; // 配置化路径
        String htmlContent;
        try {
            htmlContent = Files.readString(java.nio.file.Paths.get(htmlFilePath));
        } catch (IOException e) {
            ctx.result("服务器内部错误, 缺少关键文件, 请稍后再试");
            return;
        }

        ctx.result(
            htmlContent
                .replace("%RESULT%", result.success)
                .replace("%MESSAGE%", result.message)
        );
    }

    private static Result checkRegister(@NotNull Context ctx) {
        String account = ctx.formParam("account");
        String email = ctx.formParam("email");
        String password = ctx.formParam("password");
        String passwordV2 = ctx.formParam("password_v2");

        // 输入验证
        if (account == null || account.trim().isEmpty() || account.length() > 20) {
            return new Result("error", "注册失败, 账户名为空或字符大于20");
        }
        if (email == null || email.length() < 6 || email.length() > 50 || !Utils.isValidEmail(email)) {
            // todo 服务端发送邮件激活
            return new Result("error", "注册失败, 邮箱为空或格式不正确");
        }
        if (password == null || passwordV2 == null || password.isEmpty() || passwordV2.isEmpty() || password.length() > 20) {
            return new Result("error", "注册失败, 密码为空或长度大于20位");
        }
        if (!password.equals(passwordV2)) {
            return new Result("error", "注册失败, 请确认第二次输入密码正确");
        }

        // 创建账户 并返回是否成功 (null 为失败)
        var ret = DatabaseHelper.createAccountWithPassword(account, password, email);
        if (ret == null) {
            return new Result("error", "注册失败, 用户名已被占用或其他原因");
        }

        return new Result(
            "success",
            "验证通过, " + "账户:" + ret.getUsername() + " 密码:" + ret.getPassword() + "; 登录格式:"
                + ret.getUsername() + "&&" + ret.getPassword());
    }


    public static class registerRouter implements Router {
        @Override
        public void applyRoutes(Javalin javalin) {
            javalin.get("/account/register", ctx -> {
                // Send file
                File file = new File("./account/register.html");
                if (!file.exists()) {
                    ctx.contentType(ContentType.TEXT_HTML);
                    ctx.result("The html file was not found");
                } else {
                    var filePath = file.getPath();
                    ContentType fromExtension = ContentType.getContentTypeByExtension(filePath.substring(filePath.lastIndexOf(".") + 1));
                    ctx.contentType(fromExtension != null ? fromExtension : ContentType.TEXT_HTML);
                    ctx.result(FileUtils.read(filePath));
                }
            });

            javalin.post("/account/register", RegisterHandler::handleRegistration);
        }
    }
}
