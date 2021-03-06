package com.lxinet.jeesns.service.member.impl;

import com.lxinet.jeesns.common.utils.MemberUtil;
import com.lxinet.jeesns.core.dto.ResultModel;
import com.lxinet.jeesns.core.enums.Messages;
import com.lxinet.jeesns.core.exception.NotLoginException;
import com.lxinet.jeesns.core.exception.OpeErrorException;
import com.lxinet.jeesns.core.model.Page;
import com.lxinet.jeesns.core.utils.*;
import com.lxinet.jeesns.dao.member.IMemberDao;
import com.lxinet.jeesns.common.utils.EmailSendUtil;
import com.lxinet.jeesns.model.member.Member;
import com.lxinet.jeesns.model.member.ValidateCode;
import com.lxinet.jeesns.service.member.IMemberFansService;
import com.lxinet.jeesns.service.member.IMemberService;
import com.lxinet.jeesns.service.member.IScoreDetailService;
import com.lxinet.jeesns.service.member.IValidateCodeService;
import com.lxinet.jeesns.service.system.IActionLogService;
import com.lxinet.jeesns.service.system.IConfigService;
import com.lxinet.jeesns.common.utils.ActionUtil;
import com.lxinet.jeesns.common.utils.ConfigUtil;
import com.lxinet.jeesns.common.utils.ScoreRuleConsts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Created by zchuanzhao on 16/9/29.
 */
@Service("memberService")
public class MemberServiceImpl implements IMemberService {
    @Resource
    private IMemberDao memberDao;
    @Resource
    private IValidateCodeService validateCodeService;
    @Resource
    private IConfigService configService;
    @Resource
    private IActionLogService actionLogService;
    @Resource
    private IMemberFansService memberFansService;
    @Resource
    private IScoreDetailService scoreDetailService;

    @Override
    public boolean login(Member member, HttpServletRequest request) {
        Map<String,String> config = configService.getConfigToMap();
        if("0".equals(config.get(ConfigUtil.MEMBER_LOGIN_OPEN))){
            throw new OpeErrorException(Messages.LOGIN_CLOSED);
        }
        String password = member.getPassword();
        member.setPassword(Md5Util.getMD5Code(member.getPassword()));
        Member findMember = memberDao.login(member);
        if (null == findMember){
            actionLogService.save(IpUtil.getIpAddress(request),null,ActionUtil.MEMBER_LOGIN_ERROR,"??????????????????"+member.getName()+"??????????????????"+password);
            throw new OpeErrorException(Messages.LOGIN_INFO_WRONG);
        }
        if(findMember.getStatus() == -1){
            throw new OpeErrorException(Messages.ACCOUNT_IS_DISABLED);
        }
        //????????????????????????
        memberDao.loginSuccess(findMember.getId(), IpUtil.getIpAddress(request));
        findMember = this.findById(findMember.getId());
        MemberUtil.setLoginMember(request,findMember);
        actionLogService.save(findMember.getCurrLoginIp(),findMember.getId(), ActionUtil.MEMBER_LOGIN);
        //????????????
        scoreDetailService.scoreBonus(findMember.getId(), ScoreRuleConsts.LOGIN);
        return true;
    }

    @Override
    public Member manageLogin(Member member,HttpServletRequest request) {
        String password = member.getPassword();
        member.setPassword(Md5Util.getMD5Code(member.getPassword()));
        Member findMember = memberDao.manageLogin(member);
        if(findMember != null){
            //????????????????????????
            memberDao.loginSuccess(findMember.getId(), IpUtil.getIpAddress(request));
            findMember = this.findById(findMember.getId());
        }else {
            actionLogService.save(IpUtil.getIpAddress(request),null,ActionUtil.MEMBER_LOGIN_ERROR,"??????????????????"+member.getName()+"??????????????????"+password);
        }
        return findMember;
    }

    @Override
    public Member findById(int id) {
        return memberDao.findById(id);
    }

    @Override
    @Transactional
    public ResultModel register(Member member, HttpServletRequest request) {
        if(memberDao.findByName(member.getName()) != null){
            throw new OpeErrorException(Messages.USERNAME_EXISTS);
        }
        if(memberDao.findByEmail(member.getEmail()) != null){
            throw new OpeErrorException(Messages.EMAIL_EXISTS);
        }
        member.setRegip(IpUtil.getIpAddress(request));
        member.setPassword(Md5Util.getMD5Code(member.getPassword()));
        member.setAvatar(Const.DEFAULT_AVATAR);
        if(memberDao.register(member) == 1){
            actionLogService.save(member.getRegip(),member.getId(),ActionUtil.MEMBER_REG);
            //????????????
            scoreDetailService.scoreBonus(member.getId(),ScoreRuleConsts.REG_INIT);
            return new ResultModel(2,"????????????",request.getServletContext().getContextPath()+"/member/login");
        }
        return new ResultModel(-1,"????????????");
    }

    @Override
    public ResultModel update(Member member) {
        if(memberDao.update(member) == 1){
            return new ResultModel(3,"????????????");
        }
        return new ResultModel(-1,"????????????");
    }

    @Override
    public ResultModel delete(int id) {
        if(memberDao.delete(id) == 1){
            return new ResultModel(1,"????????????");
        }
        return new ResultModel(-1,"????????????");
    }

    @Override
    public ResultModel<Member> listByPage(Page page, String key) {
        if (StringUtils.isNotBlank(key)){
            key = "%"+key.trim()+"%";
        }
        List<Member> list = memberDao.listByPage(page, key);
        ResultModel model = new ResultModel(0,page);
        model.setData(list);
        return model;
    }

    @Override
    public ResultModel<Member> managerList(Page page, String key) {
        if (StringUtils.isNotBlank(key)){
            key = "%"+key.trim()+"%";
        }
        List<Member> list = memberDao.managerList(page, key);
        ResultModel model = new ResultModel(0,page);
        model.setData(list);
        return model;
    }

    @Override
    public ResultModel managerAdd(Member loginMember, String name) {
        int isAdmin = 1;
        Member findMember = this.findByName(name);
        if(findMember == null){
            return new ResultModel(-1,"??????["+name+"]?????????");
        }
        //???????????????????????????
        if(findMember.getId() == 1 && findMember.getIsAdmin() == 1){
            isAdmin = 2;
        }
        if(isAdmin == 1 && loginMember.getId().intValue() == findMember.getId().intValue()){
            return new ResultModel(-1,"???????????????????????????");
        }
        if(isAdmin != 2 && findMember.getIsAdmin() > 0){
            return new ResultModel(-1,"??????["+name+"]????????????????????????????????????");
        }
        //??????????????????????????????????????????
        memberDao.managerAddAndCancel(isAdmin,findMember.getId());
        if(isAdmin == 2){
            loginMember.setIsAdmin(isAdmin);
        }
        return new ResultModel(3,"????????????");
    }

    @Override
    public ResultModel managerCancel(Member loginMember, int id) {
        Member findMember = this.findById(id);
        if(loginMember.getId().intValue() == findMember.getId().intValue()){
            return new ResultModel(-1,"???????????????????????????");
        }
        if(findMember == null){
            return new ResultModel(-1,"???????????????");
        }
        memberDao.managerAddAndCancel(0,findMember.getId());
        return new ResultModel(1,"????????????");
    }


    /**
     * ???????????????????????????
     * @param id
     * @return
     */
    @Override
    public ResultModel isenable(int id) {
        if(memberDao.isenable(id) == 1){
            return new ResultModel(1,"????????????");
        }
        return new ResultModel(-1,"????????????");
    }

    /**
     * ??????????????????
     * @param id
     * @param password
     * @return
     */
    @Override
    public ResultModel changepwd(Member loginMember, int id, String password) {
        if(StringUtils.isBlank(password)){
            throw new OpeErrorException(Messages.PASSWORD_NOT_EMPTY);
        }
        if(password.length() < 6){
            return new ResultModel(-1,"??????????????????6?????????");
        }
        password = Md5Util.getMD5Code(password);
        if(memberDao.changepwd(id,password) == 1){
            actionLogService.save(loginMember.getCurrLoginIp(),loginMember.getId(),ActionUtil.CHANGE_PWD);
            return new ResultModel(3,"??????????????????");
        }
        return new ResultModel(-1,"??????????????????");
    }

    /**
     * ??????????????????
     * @param loginMember
     * @param oldPassword
     * @param newPassword
     * @return
     */
    @Override
    public ResultModel changepwd(Member loginMember, String oldPassword, String newPassword) {
        if(StringUtils.isBlank(newPassword)){
            throw new OpeErrorException(Messages.PASSWORD_NOT_EMPTY);
        }
        if(newPassword.length() < 6){
            return new ResultModel(-1,"??????????????????6?????????");
        }
        oldPassword = Md5Util.getMD5Code(oldPassword);
        Member member = memberDao.findById(loginMember.getId());
        if(!oldPassword.equals(member.getPassword())){
            return new ResultModel(-1,"???????????????");
        }
        return this.changepwd(loginMember,member.getId(),newPassword);
    }

    /**
     * ????????????
     * @param member
     * @param oldAvatar ?????????
     * @param request
     * @return
     */
    @Override
    public ResultModel updateAvatar(Member member, String oldAvatar, HttpServletRequest request) {
        int result = memberDao.updateAvatar(member.getId(),member.getAvatar());
        if(result == 1){
            if(StringUtils.isNotEmpty(oldAvatar) && !Const.DEFAULT_AVATAR.equals(oldAvatar)){
                //??????????????????
                String realPath = request.getServletContext().getRealPath(oldAvatar);
                //???????????????
                File file = new File(realPath);
                if(file.exists()){
                    file.delete();
                }
            }
            return new ResultModel(0,"??????????????????");
        }
        return new ResultModel(-1,"??????????????????????????????");
    }

    /**
     * ????????????????????????
     * @param member ????????????
     * @param name  ??????
     * @param sex  ??????
     * @param introduce  ????????????
     * @return
     */
    @Override
    public ResultModel editBaseInfo(Member member, String name, String sex, String introduce) {
        if(!StringUtils.checkNickname(member.getName())){
            return new ResultModel(-1,"???????????????????????????????????????????????????(_)???????????????(-)??????");
        }
        if (name != null && !name.equals(member.getName())){
            if(this.findByName(name) != null){
                return new ResultModel(-1,"????????????????????????????????????");
            }
        }
        member.setName(name);
        member.setSex(sex);
        member.setIntroduce(introduce);
        if(memberDao.editBaseInfo(member) == 1){
            return new ResultModel(0,"????????????");
        }
        return new ResultModel(-1,"????????????");
    }

    /**
     * ????????????????????????
     * @param loginMember ????????????
     * @param birthday
     * @param qq
     * @param wechat
     * @param contactPhone
     * @param contactEmail
     * @param website
     * @return
     */
    @Override
    public ResultModel editOtherInfo(Member loginMember, String birthday, String qq, String wechat, String contactPhone,
                                     String contactEmail, String website) {
        loginMember.setBirthday(birthday);
        loginMember.setQq(qq);
        loginMember.setWechat(wechat);
        loginMember.setContactPhone(contactPhone);
        loginMember.setContactEmail(contactEmail);
        loginMember.setWebsite(website);
        if(memberDao.editOtherInfo(loginMember) == 1){
            return new ResultModel(0,"????????????");
        }
        return new ResultModel(-1,"????????????");
    }

    @Override
    public Member findByName(String name) {
        return memberDao.findByName(name);
    }

    @Override
    public ResultModel sendEmailActiveValidCode(Member loginMember, HttpServletRequest request) {
        loginMember = this.findById(loginMember.getId());
        if(loginMember.getIsActive() == 1){
            return new ResultModel(-1,"?????????????????????????????????????????????");
        }
        String randomCode = RandomCodeUtil.randomCode6();
        ValidateCode validateCode = new ValidateCode(loginMember.getEmail(),randomCode,2);
        if(validateCodeService.save(validateCode)){
            if(EmailSendUtil.activeMember(request, loginMember.getEmail(),randomCode)){
                return new ResultModel(0,"??????????????????");
            }
        }
        return new ResultModel(-1,"??????????????????????????????");
    }

    @Transactional
    @Override
    public ResultModel active(Member loginMember, String randomCode, HttpServletRequest request) {
        try {
            loginMember = this.findById(loginMember.getId());
            if(loginMember.getIsActive() == 1){
                return new ResultModel(-1,"?????????????????????????????????????????????");
            }
            ValidateCode validateCode = validateCodeService.valid(loginMember.getEmail(),randomCode,2);
            if(validateCode == null){
                return new ResultModel(-1,"???????????????");
            }

            if(validateCodeService.used(validateCode.getId())){
                if(memberDao.active(loginMember.getId()) == 1){
                    loginMember.setIsActive(1);
                    MemberUtil.setLoginMember(request,loginMember);
                    //??????????????????
                    scoreDetailService.scoreBonus(loginMember.getId(), ScoreRuleConsts.EMAIL_AUTHENTICATION);
                    return new ResultModel(2,"???????????????????????????????????????...",request.getContextPath()+"/member/");
                }
            }
            return new ResultModel(-1,"????????????????????????");
        }catch (Exception e){
            e.printStackTrace();
            return new ResultModel(-1,"????????????????????????");
        }
    }

    @Override
    public Member findByNameAndEmail(String name, String email) {
        return memberDao.findByNameAndEmail(name,email);
    }

    @Override
    public ResultModel forgetpwd(String name, String email, HttpServletRequest request) {
        Member member = this.findByNameAndEmail(name,email);
        if(member == null){
            return new ResultModel(-1,"???????????????");
        }
        String randomCode = RandomCodeUtil.uuid();
        ValidateCode validateCode = new ValidateCode(email,randomCode,1);
        if(validateCodeService.save(validateCode)){
            if(EmailSendUtil.forgetpwd(request, email,randomCode)){
                return new ResultModel(0,"??????????????????");
            }
        }
        return new ResultModel(-1,"??????????????????????????????");
    }

    @Transactional
    @Override
    public ResultModel resetpwd(String email, String token, String password, HttpServletRequest request) {
        Member member = memberDao.findByEmail(email);
        if(member == null){
            return new ResultModel(-1,"???????????????");
        }
        ValidateCode validateCode = validateCodeService.valid(email,token,1);
        if(validateCode == null){
            return new ResultModel(-1,"???????????????");
        }
        password = Md5Util.getMD5Code(password);
        if(memberDao.changepwd(member.getId(),password) == 1){
            validateCodeService.used(validateCode.getId());
            actionLogService.save(IpUtil.getIpAddress(request),member.getId(), ActionUtil.FIND_PWD);
            return new ResultModel(2,"??????????????????",request.getContextPath()+"/member/login");
        }
        return new ResultModel(-1,"??????????????????");
    }

    @Transactional
    @Override
    public ResultModel follows(Member loginMember, Integer followWhoId) {
        if(loginMember == null){
            throw new NotLoginException();
        }
        if(this.findById(followWhoId) == null){
            return new ResultModel(-1,"????????????????????????");
        }
        if(loginMember.getId().intValue() == followWhoId.intValue()){
            return new ResultModel(-1,"??????????????????");
        }
        if(memberFansService.find(loginMember.getId(),followWhoId) == null){
            //??????
            memberFansService.save(loginMember.getId(),followWhoId);
            memberDao.follows(loginMember.getId());
            memberDao.fans(followWhoId);
            return new ResultModel(1,"????????????");
        }else {
            //????????????
            memberFansService.delete(loginMember.getId(),followWhoId);
            memberDao.follows(loginMember.getId());
            memberDao.fans(followWhoId);
            return new ResultModel(0,"??????????????????");
        }
    }

    @Override
    public ResultModel isFollowed(Member loginMember, Integer followWhoId) {
        int loginMemberId = 0;
        if(loginMember != null){
            loginMemberId = loginMember.getId().intValue();
        }
        if(memberFansService.find(loginMemberId,followWhoId) == null){
            return new ResultModel(0,"?????????");
        }else {
            return new ResultModel(1,"?????????");
        }
    }

    @Override
    public List<Member> listContactMemberIds(Page page, Integer memberId) {
        List<Member> list = memberDao.listContactMemberIds(page, memberId);
        return list;
    }


    /**
     * ?????????????????????????????????
     * @param page
     * @param memberId
     * @return
     */
    @Override
    public ResultModel<Member> listContactMembers(Page page, Integer memberId) {
        List<Member> memberIdList = this.listContactMemberIds(page,memberId);
        List<Member> list = new ArrayList<>();
        if(memberIdList.size() > 0){
            List<Integer> idList = new ArrayList<>();
            String idString = "";
            for (Member member : memberIdList){
                if (member != null){
                    idList.add(member.getId());
                    idString += member.getId() + ",";
                }
            }
            if (idString.length() > 0){
                idString = idString.substring(0,idString.length()-1);
            }
            if (StringUtils.isNotEmpty(idString)){
                list = memberDao.listContactMembers(memberId, idList, idString);
            }
        }
        ResultModel model = new ResultModel(0, page);
        model.setData(list);
        return model;
    }

    /**
     * ??????????????????
     * @param score
     * @param memberId
     * @return
     */
    @Override
    public boolean updateScore(Integer score, Integer memberId) {
        return memberDao.updateScore(score,memberId) == 1;
    }

    @Override
    public String atFormat(String content) {
        List<String> nameList = AtUtil.getAtNameList(content);
        for (String name : nameList){
            Member member = this.findByName(name);
            if (member != null){
                content = AtUtil.replaceAt(content,name,member.getId());
            }
        }
        return content;
    }
}
