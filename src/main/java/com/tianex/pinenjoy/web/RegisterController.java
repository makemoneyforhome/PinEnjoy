package com.tianex.pinenjoy.web;

import com.tianex.pinenjoy.core.Constant;
import com.tianex.pinenjoy.domain.Account;
import com.tianex.pinenjoy.domain.EmailCheck;
import com.tianex.pinenjoy.domain.Image;
import com.tianex.pinenjoy.service.AccountService;
import com.tianex.pinenjoy.service.EmailCheckService;
import com.tianex.pinenjoy.util.DateUtils;
import com.tianex.pinenjoy.util.NumberUtils;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.sql.Date;

/**
 * 实现注册功能的控制器类
 * @author TianEx
 */
@Controller
@RequestMapping
public class RegisterController {

    private AccountService accountService;
    private EmailCheckService emailCheckService;
    private JmsTemplate jmsTemplate;

    @RequestMapping(value = "/register", method = RequestMethod.GET)
    public String showRegisterForm() {
        return "register";
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public String register(@RequestParam("thumbFile") final MultipartFile thumbFile, Account account,
                           HttpServletRequest request) {
        if (thumbFile.isEmpty()) {
            return "register";
        }

        String location = Constant.USER_IMAGE_LOCATION + "/" + thumbFile.getOriginalFilename();

        jmsTemplate.send("pp.uploadImage.queue", new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return session.createObjectMessage((Serializable) thumbFile);
            }
        });

        account.setAccountThumb(location);

        try {
            accountService.createAccount(account);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        return "success";
    }

    @RequestMapping("/register/{emailCheck}")
    public String checkForEmail(@PathVariable String emailCheck, Model model, HttpServletRequest request) {
        if (emailCheck.length() != 32 || emailCheck == null) {
            model.addAttribute(Constant.ERROR_MSG, "无效的验证码！");
        }

        EmailCheck emailCheckInstance = emailCheckService.findEmailCheckByCode(emailCheck);
        if (emailCheckInstance == null) {
            model.addAttribute(Constant.ERROR_MSG, "无效的验证码！");
        }

        boolean available = DateUtils.compareTimeForAvailableToCheck(emailCheckInstance.getEmailCheckGenerateTime());
        if (!available) {
            model.addAttribute(Constant.ERROR_MSG, "验证码已过期，请重新验证");
        }

        Account currentAccount = accountService.findAccountByUsername(emailCheckInstance.getEmailCheckAccountNickname());
        currentAccount.setAccountIsLock(true);
        accountService.updateAccount(currentAccount);

        model.addAttribute("currentAccount", currentAccount);

        return "checkForEmail";
    }

    @Resource
    public void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Resource
    public void setEmailCheckService(EmailCheckService emailCheckService) {
        this.emailCheckService = emailCheckService;
    }

    @Resource
    public void setJmsTemplate(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }
}
