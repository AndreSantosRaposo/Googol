package webServer.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MenuController {

    @GetMapping("/menu")
    public String showMenu(Model model) {
        return "mainMenu";
    }

    @PostMapping("/addUrl")
    public String indexURL(@RequestParam("url") String url, Model model) {
        System.out.println("[PlaceHolder] A adicionar URL: " + url);
        model.addAttribute("mensagem", "URL indexada com sucesso: " + url);
        model.addAttribute("tipo", "sucesso");
        return "mainMenu";
    }
}
