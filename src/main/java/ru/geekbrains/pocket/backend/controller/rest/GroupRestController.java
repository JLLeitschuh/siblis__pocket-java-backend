package ru.geekbrains.pocket.backend.controller.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.lang.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.geekbrains.pocket.backend.domain.db.Group;
import ru.geekbrains.pocket.backend.domain.db.GroupMember;
import ru.geekbrains.pocket.backend.domain.db.User;
import ru.geekbrains.pocket.backend.domain.pub.GroupPub;
import ru.geekbrains.pocket.backend.enumeration.RoleGroupMember;
import ru.geekbrains.pocket.backend.service.GroupMemberService;
import ru.geekbrains.pocket.backend.service.GroupService;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.List;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class GroupRestController {
    private static final Logger logger = LoggerFactory.getLogger(GroupRestController.class.getName());

    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupMemberService groupMemberService;
    @Autowired
    private HttpRequestComponent httpRequestComponent;

    @GetMapping("/groups/{id}") //Получить информацию о группе
    public ResponseEntity<?> getGroup(@PathVariable String id,
                                      @Valid @RequestBody InvitationCodeRequest invitationCodeRequest) {
        Group group = groupService.getGroup(new ObjectId(id));
        if (group != null) {
            if (group.getInvitation_code() == null ||
                    (group.getInvitation_code() != null
                    && group.getInvitation_code().equals(invitationCodeRequest.getInvitation_code())))
                return new ResponseEntity<>(new GroupPub(group), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PostMapping("/groups") //Создать группу
    public ResponseEntity<?> createGroup(@Valid @RequestBody NewGroupRequest newGroupRequest,
                                         HttpServletRequest request) {
        User user = httpRequestComponent.getUserFromToken(request);
        if (user != null) {
            Group group = new Group();
            group.setCreator(user);
            //group.setProject();
            group.setName(newGroupRequest.getName());
            if (newGroupRequest.getDescription() != null)
                group.setDescription(newGroupRequest.getDescription());
            //group.setInvitation_code("");
            group.setPublic(newGroupRequest.isPublic);
            group = groupService.createGroupAndMember(group, user);
            if (group != null) {
                return new ResponseEntity<>(new GroupPub(group), HttpStatus.OK);
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PutMapping("/groups/{id}") //Изменить информацию о группе
    public ResponseEntity<?> editGroup(@PathVariable String id,
                                       @Valid @RequestBody EditGroupRequest groupRequest,
                                       HttpServletRequest request) {
        Group group = groupService.getGroup(new ObjectId(id));
        User user = httpRequestComponent.getUserFromToken(request);
        if (group != null && user != null) {
            GroupMember groupMember = groupMemberService.getGroupMember(group, user);
            //изменять можно только администратору
            if (groupMember != null && groupMember.getRole().equals(RoleGroupMember.administrator)) {
                if (groupRequest.getName() != null)
                    group.setName(groupRequest.getName());
                if (groupRequest.getDescription() != null)
                    group.setDescription(groupRequest.getDescription());
                group.setPublic(groupRequest.isPublic);
                group = groupService.updateGroup(group);

                if (group != null) {
                    return new ResponseEntity<>(new GroupPub(group), HttpStatus.OK);
                }
            } else {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @RequestMapping("/groups/{id}") //Вступить в группу //Покинуть группу
    public ResponseEntity<?> joinOrLeaveTheGroup(@PathVariable String id,
                                         HttpServletRequest request) {
        InvitationCodeRequest invitationCodeRequest = null;
        try {
            invitationCodeRequest = new ObjectMapper().readValue(request.getReader(), InvitationCodeRequest.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (request.getMethod().equals("LINK")) {
            logger.info("LINK METHOD"); //Вступить в группу
            User user = httpRequestComponent.getUserFromToken(request);
            Group group = groupService.getGroup(new ObjectId(id));
            if (invitationCodeRequest != null && user != null
                    && group != null && group.getInvitation_code() != null) {
                if (group.getInvitation_code().equals(invitationCodeRequest.getInvitation_code())) {
                    GroupMember groupMember = groupMemberService.getGroupMember(group, user);
                    if (groupMember == null) {
                        groupMemberService.createGroupMember(group, user, RoleGroupMember.speacker);
                    }
                    return new ResponseEntity<>(new GroupPub(group), HttpStatus.OK);
                }
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else if (request.getMethod().equals("UNLINK")) {
            logger.info("UNLINK METHOD"); //Покинуть группу
            User user = httpRequestComponent.getUserFromToken(request);
            Group group = groupService.getGroup(new ObjectId(id));
            if (user != null && group != null) {
                GroupMember groupMember = groupMemberService.getGroupMember(group, user);
                if (groupMember != null) {
                    groupMemberService.deleteGroupMember(groupMember);
                }
                return new ResponseEntity<>(HttpStatus.OK);
            }
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        } else {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
    }

//    //TODO replace to LINK method
//    @PostMapping("/groups/{id}") //Вступить в группу
//    //@RequestMapping("/groups/{id}") //Вступить в группу
//    public ResponseEntity<?> joinToGroup(@PathVariable String id,
//                                         @Valid @RequestBody InvitationCodeRequest invitationCodeRequest,
//                                         HttpServletRequest request) {
//        User user = httpRequestComponent.getUserFromToken(request);
//        Group group = groupService.getGroup(new ObjectId(id));
//        if (invitationCodeRequest != null && user != null
//                && group != null && group.getInvitation_code() != null) {
//            if (group.getInvitation_code().equals(invitationCodeRequest.getInvitation_code())) {
//                //TODO add to GroupMember
//                List<GroupMember> groupMembers = groupMemberService.getGroupMembers(group);
//                GroupMember groupMember = groupMemberService.getGroupMember(group, user);
//                if (groupMember == null) {
//                    groupMemberService.createGroupMember(group, user, RoleGroupMember.speacker);
//                }
//                return new ResponseEntity<>(new GroupPub(group), HttpStatus.OK);
//            }
//        }
//        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//    }
//
//    //TODO replace to UNLINK method
//    @DeleteMapping("/groups/{id}") //Покинуть группу
////    @RequestMapping("/groups/{id}") //Покинуть группу
//    //@RequestMapping ( path = "/groups/{id}", method = RequestMethod.DELETE)
//    public ResponseEntity<?> leaveTheGroup(@PathVariable String id,
//                                           HttpServletRequest request) {
//        User user = httpRequestComponent.getUserFromToken(request);
//        Group group = groupService.getGroup(new ObjectId(id));
//        if (user != null && group != null) {
//            GroupMember groupMember = groupMemberService.getGroupMember(group, user);
//            if (groupMember != null) {
//                groupMemberService.deleteGroupMember(groupMember);
//            }
//                return new ResponseEntity<>(HttpStatus.OK);
//        }
//        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
//    }

    //===== Request & Response =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InvitationCodeRequest {

        //@Nullable
        private String invitation_code;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class NewGroupRequest {

        @NotNull
        @Size(min = 6, max = 32)
        private String name;

        @Nullable
        private String description;

        @JsonProperty("public")
        private boolean isPublic = false;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class EditGroupRequest {

        @Nullable
        @Size(min = 6, max = 32)
        private String name;

        @Nullable
        private String description;

        @JsonProperty("public")
        private boolean isPublic = false;

    }
}
