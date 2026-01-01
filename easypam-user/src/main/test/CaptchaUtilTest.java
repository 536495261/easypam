import cn.hutool.crypto.digest.BCrypt;
import com.neu.easypam.user.UserApplication;
import com.neu.easypam.user.util.CaptchaUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

//@SpringBootTest(classes = UserApplication.class)
public class CaptchaUtilTest {

    @Resource
    private CaptchaUtil captchaUtil;

    // 自定义的MockServletOutputStream（内部类）
    class MockServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream outputStream;

        public MockServletOutputStream(ByteArrayOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
        }
    }

    @Test
    public void testGenerateCaptchaImage() {
        // 1. 生成验证码字符串（注意：原方法名是generateCaptchaCode，不是generateCaptcha，需修正）
        String code = captchaUtil.generateCaptcha();
        System.out.println("测试图片生成的验证码：" + code);

        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ServletOutputStream mockServletOutputStream = new MockServletOutputStream(byteArrayOutputStream);

        try {
            Mockito.when(mockResponse.getOutputStream()).thenReturn(mockServletOutputStream);
            // 2. 生成验证码图片流
            captchaUtil.generateCaptchaImage(code, mockResponse);

            // 3. 获取图片字节数组
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            System.out.println("生成的验证码图片字节数：" + imageBytes.length);
            org.junit.jupiter.api.Assertions.assertTrue(imageBytes.length > 0, "验证码图片生成失败（流为空）");

            // ========== 核心新增：将图片保存到本地 ==========
            // 保存路径：项目根目录下的 captcha-test.png（可自定义路径）
            String savePath = "captcha-test.png";
            FileOutputStream fos = new FileOutputStream(savePath);
            fos.write(imageBytes);
            fos.flush();
            fos.close();
            System.out.println("验证码图片已保存到：" + System.getProperty("user.dir") + "/" + savePath);

        } catch (Exception e) {
            e.printStackTrace();
            org.junit.jupiter.api.Assertions.fail("生成验证码图片异常：" + e.getMessage());
        }
    }
    @Test
    public void testBCrypt(){
        String hash = BCrypt.hashpw("123456");
        System.out.println(hash);
    }
}