package com.example.board;

import com.example.board.entity.Post;
import com.example.board.service.PostService;
import com.example.board.support.ConcurrencyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 찾아 바꾸기는 전부 적용되거나 전혀 적용되지 않아야 한다. */
class ReplaceWordAtomicityTest extends ConcurrencyTestSupport {

    @Autowired
    PostService service;

    private Post save(String title, String content) {
        Post post = new Post();
        post.setTitle(title);
        post.setName("tester");
        post.setContent(content);
        return repository.save(post);
    }

    private Post reload(Post post) {
        return repository.findById(post.getId()).orElseThrow();
    }

    @Test
    void 정상_치환은_모든_글에_반영된다() {
        Post a = save("kfc 후기", "오늘 kfc에서 먹었다");
        Post b = save("점심 kfc", "내용");

        int changed = service.replaceWord(List.of(a.getId(), b.getId()), "kfc", "bhc");

        assertThat(changed).isEqualTo(2);
        assertThat(reload(a).getTitle()).isEqualTo("bhc 후기");
        assertThat(reload(a).getContent()).isEqualTo("오늘 bhc에서 먹었다");
        assertThat(reload(b).getTitle()).isEqualTo("점심 bhc");
    }

    @Test
    void 제목이_비게_되는_글이_있으면_앞_글도_원래대로_남는다() {
        Post a = save("kfc 후기", "내용");
        Post b = save("kfc", "내용");   // kfc를 지우면 제목이 비어 저장할 수 없다

        Throwable thrown = catchThrowable(
                () -> service.replaceWord(List.of(a.getId(), b.getId()), "kfc", ""));

        assertThat(reload(a).getTitle()).as("앞 글이 이미 바뀌어 있으면 안 된다").isEqualTo("kfc 후기");
        assertThat(reload(b).getTitle()).isEqualTo("kfc");
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 제목이_100자를_넘기는_글이_있으면_앞_글도_원래대로_남는다() {
        Post a = save("kfc 후기", "내용");
        Post b = save("kfc kfc kfc kfc kfc kfc kfc kfc", "내용");   // 치환하면 100자 초과

        Throwable thrown = catchThrowable(() -> service.replaceWord(
                List.of(a.getId(), b.getId()), "kfc", "치킨 전문점 (프랜차이즈)"));

        assertThat(reload(a).getTitle()).as("앞 글이 이미 바뀌어 있으면 안 된다").isEqualTo("kfc 후기");
        assertThat(reload(b).getTitle()).isEqualTo("kfc kfc kfc kfc kfc kfc kfc kfc");
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void API는_실패하면_400을_주고_아무것도_바꾸지_않는다() {
        Post a = save("kfc 후기", "내용");
        Post b = save("kfc", "내용");

        int status = post("/testReplace", "ids=" + a.getId() + "," + b.getId() + "&before=kfc&after=");

        assertThat(status).isEqualTo(400);
        assertThat(reload(a).getTitle()).isEqualTo("kfc 후기");
        assertThat(reload(b).getTitle()).isEqualTo("kfc");
    }
}
