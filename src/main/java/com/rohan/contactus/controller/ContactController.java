package com.rohan.contactus.controller;

import com.rohan.contactus.entity.Contact;
import com.rohan.contactus.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



import java.util.List;


@RestController
@RequestMapping("/contact")
public class ContactController {
    @Autowired
    private ContactService contactService;

    @PostMapping
    public ResponseEntity<String> submitContact(@RequestBody Contact contact) {
        contactService.saveContact(contact);
        return ResponseEntity.ok("I will contact you soon!");
    }

    @GetMapping
    public List<Contact> getAllContacts() {
        return contactService.getAllContacts();
    }

}
