package emu.grasscutter.server.http.objects;

public class LoginAccountRequestJson {
    public String account;
    public String password;
    public boolean is_crypto;

    // 在使用一体化密码时分割账号和密码
    public void parse() {
        if (account != null && account.contains("&&")) {
            String[] parts = account.split("&&", 2);
            if (parts.length == 2) {
                this.account = parts[0];
                this.password = parts[1];
                return;
            }
        }

        // 处理分割后数组长度不为2以及格式不符合要求的情况
        this.account = null;
        this.password = null;
    }
}
