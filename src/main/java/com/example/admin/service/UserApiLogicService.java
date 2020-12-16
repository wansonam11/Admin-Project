package com.example.admin.service;

import com.example.admin.ifs.CrudInterface;
import com.example.admin.model.entity.Item;
import com.example.admin.model.entity.OrderGroup;
import com.example.admin.model.entity.User;
import com.example.admin.model.entity.enumclass.UserStatus;
import com.example.admin.model.network.Header;
import com.example.admin.model.network.Pagination;
import com.example.admin.model.network.request.UserApiRequest;
import com.example.admin.model.network.response.ItemApiResponse;
import com.example.admin.model.network.response.OrderGroupApiResponse;
import com.example.admin.model.network.response.UserApiResponse;
import com.example.admin.model.network.response.UserOrderInfoApiResponse;
import com.example.admin.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserApiLogicService implements CrudInterface<UserApiRequest, UserApiResponse> {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderGroupApiLogicService orderGroupApiLogicService;

    @Autowired
    private ItemApiLogicService itemApiLogicService;

    //1. request data
    //2. user 생성
    //3. 생성된 데이터 -> UserApiResponse를 return 해준다.


    @Override
    public Header<UserApiResponse> create(Header<UserApiRequest> request) {

        //1. request date
        UserApiRequest userApiRequest = request.getData();

        //2. User 생성
        User user = User.builder()
                .account(userApiRequest.getAccount())
                .password(userApiRequest.getPassword())
                .status(UserStatus.REGISTERED)
                .phoneNumber(userApiRequest.getPhoneNumber())
                .email(userApiRequest.getEmail())
                .registeredAt(LocalDateTime.now())
                .build();

        User newUser = userRepository.save(user);

        //3. 생성된 데이터 -> userApiResponse로 return 해준다.
        return Header.OK(response(newUser));

    }

    @Override
    public Header<UserApiResponse> read(Long id) {

        return userRepository.findById(id)
                .map(user -> response(user))
                .map(Header::OK)
                .orElseGet(() -> Header.Error("데이터 없음"));
    }

    @Override
    public Header<UserApiResponse> update(Header<UserApiRequest> request) {

        //데이터를 가져와서
        UserApiRequest userApiRequest = request.getData();

        // 2. id를 가지고 user 데이터를 찾고,
        Optional<User> optional = userRepository.findById(userApiRequest.getId());

        return optional.map(user -> {

            user.setAccount(userApiRequest.getAccount())
                    .setPassword(userApiRequest.getPassword())
                    .setStatus(userApiRequest.getStatus())
                    .setPhoneNumber(userApiRequest.getPhoneNumber())
                    .setEmail(userApiRequest.getEmail())
                    .setRegisteredAt(userApiRequest.getRegisteredAt())
                    .setUnregisteredAt(userApiRequest.getUnregisteredAt());

            return user;
        })
                .map(user -> userRepository.save(user))           // 3. date -> update 시켜주고
                .map(updateUser -> response(updateUser))          // 4. userApiResponse
                .map(Header::OK)
                .orElseGet(() -> Header.Error("데이터 없음"));
    }

    @Override
    public Header delete(Long id) {

        //1.  id를 불러와 repository를 통해 user를 찾고
        Optional<User> optional = userRepository.findById(id);

        // repository를 통해 delete 해주고,
        return optional.map(user -> {
            userRepository.delete(user);
            return Header.OK();
            })
                .orElseGet(() -> Header.Error("데이터 없음"));
    }

    private UserApiResponse response(User user){
        // 생성된 데이터(user) -> userApiReponse를 return해준다.

        UserApiResponse userApiResponse = UserApiResponse.builder()
                .id(user.getId())
                .account(user.getAccount())
                .password(user.getPassword()) //todo 암호화, 길이
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .registeredAt(user.getRegisteredAt())
                .unregisteredAt(user.getUnregisteredAt())
                .build();

        //Header + data를 합쳐서 return
        return userApiResponse;
    }

    public Header<List<UserApiResponse>> search(Pageable pageable) {

        Page<User> users = userRepository.findAll(pageable);

        List<UserApiResponse> userApiResponseList = users.stream()
                .map(user-> response(user))
                .collect(Collectors.toList());

        //List<UserApiResponse> 로 되어있는데,
        //Header<List<UserApiResponse>> 라고 만들어줘야 한다.

        Pagination pagination = Pagination.builder()
                .totalPage(users.getTotalPages())
                .totalElements((users.getTotalElements()))
                .currentPage(users.getNumber())
                .currentElements(users.getNumberOfElements())
                .build();

        return Header.OK(userApiResponseList, pagination);
    }

    public Header<UserOrderInfoApiResponse> orderInfo(Long id) {

        //user
        User user = userRepository.getOne(id);
        UserApiResponse userApiResponse = response(user);

        //orderGroup
        List<OrderGroup> orderGroupList = user.getOrderGroupList();
        List<OrderGroupApiResponse> orderGroupApiResponseList = orderGroupList.stream()
                .map(orderGroup -> {
                    OrderGroupApiResponse orderGroupApiResponse
                            = orderGroupApiLogicService.response(orderGroup).getData();
                    //item
                    List<ItemApiResponse> itemApiResponseList = orderGroup.getOrderDetailList().stream()
                            .map(detail -> detail.getItem())
                            .map(item -> itemApiLogicService.response(item).getData())
                            .collect(Collectors.toList());
                    orderGroupApiResponse.setItemApiResponseList(itemApiResponseList);

                    return orderGroupApiResponse;

                })
                .collect(Collectors.toList());

        userApiResponse.setOrderGroupList(orderGroupApiResponseList);
        UserOrderInfoApiResponse userOrderInfoApiResponse = UserOrderInfoApiResponse.builder()
                .userApiResponse(userApiResponse)
                .build();

        return Header.OK(userOrderInfoApiResponse);
    }
}
