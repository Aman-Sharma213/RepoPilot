package com.repopilot.spring;

import com.repopilot.graph.EdgeType;
import com.repopilot.graph.GraphEdge;
import com.repopilot.index.TestRepo;
import com.repopilot.resolve.ResolutionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpringAnalysisTest {

    @Test
    void fieldInjectionOfAnInterfaceResolvesToTheImplementingBean() throws Exception {
        var graph = TestRepo.of()
                .file("src/a/Api.java", "package a; public interface Api { void run(); }")
                .file("src/a/Impl.java", """
                        package a;
                        import org.springframework.stereotype.Service;
                        @Service
                        public class Impl implements Api { public void run() {} }
                        """)
                .file("src/a/Consumer.java", """
                        package a;
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
                        @Service
                        public class Consumer {
                          @Autowired private Api api;
                          public void go() { api.run(); }
                        }
                        """)
                .graph();
        List<GraphEdge> di = graph.edgesOfType(EdgeType.SPRING_DI);
        assertEquals(1, di.size());
        assertEquals("a.Consumer#api", di.get(0).source().value());
        assertEquals("a.Impl", di.get(0).target().value());
        assertEquals(ResolutionStatus.RESOLVED, di.get(0).status());
        assertTrue(di.get(0).evidence().contains("@Autowired"));
    }

    @Test
    void twoBeansForOneInterfaceAreAmbiguousUnlessQualified() throws Exception {
        var graph = TestRepo.of()
                .file("src/a/Api.java", "package a; public interface Api { void run(); }")
                .file("src/a/First.java", """
                        package a;
                        import org.springframework.stereotype.Service;
                        @Service public class First implements Api { public void run() {} }
                        """)
                .file("src/a/Second.java", """
                        package a;
                        import org.springframework.stereotype.Service;
                        @Service public class Second implements Api { public void run() {} }
                        """)
                .file("src/a/Consumer.java", """
                        package a;
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.beans.factory.annotation.Qualifier;
                        import org.springframework.stereotype.Service;
                        @Service
                        public class Consumer {
                          @Autowired private Api ambiguous;
                          @Autowired @Qualifier("second") private Api chosen;
                        }
                        """)
                .graph();
        List<GraphEdge> ambiguous = graph.edgesOfType(EdgeType.SPRING_DI).stream()
                .filter(e -> e.source().value().endsWith("#ambiguous")).toList();
        assertEquals(2, ambiguous.size(), "both candidate beans are recorded");
        assertTrue(ambiguous.stream().allMatch(e -> e.status() == ResolutionStatus.AMBIGUOUS));

        List<GraphEdge> chosen = graph.edgesOfType(EdgeType.SPRING_DI).stream()
                .filter(e -> e.source().value().endsWith("#chosen")).toList();
        assertEquals(1, chosen.size());
        assertEquals("a.Second", chosen.get(0).target().value());
    }

    @Test
    void primaryBeanWinsOverOtherImplementations() throws Exception {
        var graph = TestRepo.of()
                .file("src/a/Api.java", "package a; public interface Api {}")
                .file("src/a/First.java", """
                        package a;
                        import org.springframework.stereotype.Service;
                        @Service public class First implements Api {}
                        """)
                .file("src/a/Main.java", """
                        package a;
                        import org.springframework.context.annotation.Primary;
                        import org.springframework.stereotype.Service;
                        @Service @Primary public class Main implements Api {}
                        """)
                .file("src/a/Consumer.java", """
                        package a;
                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.stereotype.Service;
                        @Service public class Consumer { @Autowired private Api api; }
                        """)
                .graph();
        List<GraphEdge> di = graph.edgesOfType(EdgeType.SPRING_DI);
        assertEquals(1, di.size());
        assertEquals("a.Main", di.get(0).target().value());
        assertTrue(di.get(0).evidence().contains("@Autowired"));
    }

    @Test
    void constructorInjectionBeanMethodsAndEventListenersHaveTheirOwnEdgeTypes() throws Exception {
        var graph = TestRepo.of()
                .file("src/a/Dep.java", """
                        package a;
                        import org.springframework.stereotype.Component;
                        @Component public class Dep {}
                        """)
                .file("src/a/Event.java", "package a; public class Event {}")
                .file("src/a/Config.java", """
                        package a;
                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;
                        import org.springframework.context.event.EventListener;
                        @Configuration
                        public class Config {
                          private final Dep dep;
                          public Config(Dep dep) { this.dep = dep; }
                          @Bean public Dep otherDep() { return new Dep(); }
                          @EventListener public void onEvent(Event event) {}
                        }
                        """)
                .graph();
        assertTrue(graph.edgesOfType(EdgeType.SPRING_DI).stream()
                .anyMatch(e -> e.source().value().equals("a.Config#<init>(Dep)")
                        && e.target().value().equals("a.Dep")));
        assertTrue(graph.edgesOfType(EdgeType.BEAN_CREATION).stream()
                .anyMatch(e -> e.target().value().equals("a.Dep")));
        assertTrue(graph.edgesOfType(EdgeType.EVENT_LISTENER).stream()
                .anyMatch(e -> e.target().value().equals("a.Event")));
    }

    @Test
    void nonSpringCodeProducesNoSpringEdges() throws Exception {
        var graph = TestRepo.of()
                .file("src/a/Api.java", "package a; interface Api { void run(); }")
                .file("src/a/Impl.java", "package a; class Impl implements Api { public void run() {} }")
                .file("src/a/User.java", "package a; class User { private Api api; }")
                .graph();
        assertTrue(graph.edgesOfType(EdgeType.SPRING_DI).isEmpty());
        assertTrue(graph.edgesOfType(EdgeType.BEAN_CREATION).isEmpty());
        assertTrue(graph.edgesOfType(EdgeType.EVENT_LISTENER).isEmpty());
    }
}
