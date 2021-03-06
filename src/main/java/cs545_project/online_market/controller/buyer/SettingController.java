package cs545_project.online_market.controller.buyer;

import cs545_project.online_market.domain.User;
import cs545_project.online_market.helper.Util;
import cs545_project.online_market.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "/buyer/setting")
public class SettingController {

    @Autowired
    Util util;

    @Autowired
    AddressService addressService;

    @GetMapping
    public String getSetting(Model model){
        User user = util.getCurrentUser();
        model.addAttribute("user", user);
        return "views/buyer/setting";
    }
}
