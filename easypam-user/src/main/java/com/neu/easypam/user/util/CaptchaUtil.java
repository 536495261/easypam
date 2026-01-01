package com.neu.easypam.user.util;

import cn.hutool.core.util.RandomUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Random;

@Component
public class CaptchaUtil {
    // 验证码字符集（排除易混淆的0/O、1/I等）
    private static final String CAPTCHA_CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    // 验证码图片宽度
    private static final int WIDTH = 120;
    // 验证码图片高度
    private static final int HEIGHT = 40;
    // 验证码字符个数
    private static final int CODE_COUNT = 4;
    // 干扰线数量
    private static final int LINE_COUNT = 5;
    // 字体大小
    private static final int FONT_SIZE = 18;

    public String generateCaptcha() {
        return RandomUtil.randomString(CAPTCHA_CHARSET, CODE_COUNT);
    }

    /**
     * 生成验证码图片并返回Base64字符串
     * @param code 验证码字符串
     * @return Base64编码的图片（带data:image/png;base64,前缀）
     */
    public String generateCaptchaBase64(String code) {
        BufferedImage image = createCaptchaImage(code);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }

    /**
     * 创建验证码图片（内部方法）
     */
    private BufferedImage createCaptchaImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // 背景色
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 字体
        g.setFont(new Font("Arial", Font.BOLD, FONT_SIZE));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Random random = new Random();
        
        // 干扰线
        for (int i = 0; i < LINE_COUNT; i++) {
            g.setColor(getRandomLightColor(random));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), 
                       random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 验证码字符
        for (int i = 0; i < CODE_COUNT; i++) {
            char c = code.charAt(i);
            g.setColor(getRandomDarkColor(random));
            int rotateAngle = random.nextInt(60) - 30;
            g.rotate(Math.toRadians(rotateAngle), 15 + i * 25, HEIGHT / 2);
            g.drawString(String.valueOf(c), 15 + i * 25, HEIGHT / 2 + random.nextInt(10) - 5);
            g.rotate(-Math.toRadians(rotateAngle), 15 + i * 25, HEIGHT / 2);
        }

        // 干扰点
        for (int i = 0; i < 20; i++) {
            g.setColor(getRandomLightColor(random));
            g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }

        g.dispose();
        return image;
    }
    /**
     * 根据验证码字符串生成图片，并输出到HttpServletResponse
     * @param code 验证码字符串
     * @param response 响应对象（用于输出图片流）
     */
    public void generateCaptchaImage(String code, HttpServletResponse response) {
        BufferedImage image = createCaptchaImage(code);
        try {
            response.setContentType("image/png");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expires", 0);
            OutputStream out = response.getOutputStream();
            ImageIO.write(image, "PNG", out);
            out.flush();
            out.close();
        } catch (Exception e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    } 
    /**
     * 生成随机浅色系（用于干扰线/干扰点）
     */
    private Color getRandomLightColor(Random random) {
        int r = random.nextInt(200) + 55;
        int g = random.nextInt(200) + 55;
        int b = random.nextInt(200) + 55;
        return new Color(r, g, b);
    }

    /**
     * 生成随机深色系（用于验证码字符）
     */
    private Color getRandomDarkColor(Random random) {
        int r = random.nextInt(100);
        int g = random.nextInt(100);
        int b = random.nextInt(100);
        return new Color(r, g, b);
    }
}
