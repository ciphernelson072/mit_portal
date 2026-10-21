package com.sis.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sis.entity.FinancialHold;
import com.sis.entity.LeaveRequest;
import com.sis.entity.LeaveRequestStatus;
import com.sis.entity.ProcurementRequest;
import com.sis.entity.ProcurementRequestStatus;
import com.sis.entity.Role;
import com.sis.entity.Staff;
import com.sis.entity.Student;
import com.sis.entity.Supplier;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class OperationsResponseFetchTest {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private LeaveRequestRepository leaveRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FinancialHoldRepository holdRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProcurementRequestRepository procurementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void hrFinanceAndProcurementResponsesSerializeAfterTransactionsClose() throws Exception {
        User staffUser = userRepository.saveAndFlush(new User("fetch-staff", "encoded", Role.TEACHER));
        Staff staff = new Staff();
        staff.setUser(staffUser);
        staff.setEmployeeNumber("FETCH-STAFF-1");
        staff.setFullName("Fetch Staff");
        staff.setTitle("Lecturer");
        staff.setHireDate(LocalDate.of(2020, 1, 1));
        staff = staffRepository.saveAndFlush(staff);

        assertThat(objectMapper.writeValueAsString(staffRepository.findAll()))
                .contains("fetch-staff");

        LeaveRequest leave = new LeaveRequest();
        leave.setStaff(staff);
        leave.setStartDate(LocalDate.of(2026, 10, 10));
        leave.setEndDate(LocalDate.of(2026, 10, 12));
        leave.setReason("Annual leave");
        leave = leaveRepository.saveAndFlush(leave);

        List<LeaveRequest> staffLeaves = leaveRepository.findByStaffIdOrderByCreatedAtDesc(staff.getId());
        List<LeaveRequest> pendingLeaves = leaveRepository.findByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING);
        List<LeaveRequest> openStaffLeaves = leaveRepository
                .findByStaffIdAndStatus(staff.getId(), LeaveRequestStatus.PENDING);
        assertThat(objectMapper.writeValueAsString(staffLeaves)).contains("fetch-staff");
        assertThat(objectMapper.writeValueAsString(pendingLeaves)).contains("fetch-staff");
        assertThat(objectMapper.writeValueAsString(openStaffLeaves)).contains("fetch-staff");
        assertThat(objectMapper.writeValueAsString(leaveRepository.findByIdWithStaffUser(leave.getId()).orElseThrow()))
                .contains("fetch-staff");

        Student student = studentRepository.saveAndFlush(new Student(
                new User("fetch-student", "encoded", Role.STUDENT), "Fetch Student", "Year 1"));
        FinancialHold hold = new FinancialHold();
        hold.setStudent(student);
        hold.setReason("Outstanding balance");
        hold.setCreatedBy("finance-user");
        holdRepository.saveAndFlush(hold);

        List<FinancialHold> holds = holdRepository.findByStudentIdAndActiveTrueOrderByCreatedAtDesc(student.getId());
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(holds.get(0).getStudent(), "user")).isTrue();
        assertThat(objectMapper.writeValueAsString(holds)).contains("fetch-student");

        Supplier supplier = new Supplier();
        supplier.setSupplierCode("FETCH-SUP");
        supplier.setName("Fetch Supplier");
        supplier = supplierRepository.saveAndFlush(supplier);
        ProcurementRequest request = new ProcurementRequest();
        request.setRequesterId(staffUser.getId());
        request.setDescription("Test request");
        request.setEstimatedAmount(new BigDecimal("10.00"));
        request.setSupplier(supplier);
        request = procurementRepository.saveAndFlush(request);

        ProcurementRequest pending = procurementRepository
                .findByStatusOrderByCreatedAtAsc(ProcurementRequestStatus.SUBMITTED).get(0);
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(pending, "supplier")).isTrue();
        assertThat(objectMapper.writeValueAsString(pending)).contains("Fetch Supplier");

        ProcurementRequest byId = procurementRepository.findByIdWithSupplier(request.getId()).orElseThrow();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(byId, "supplier")).isTrue();
        assertThat(objectMapper.writeValueAsString(byId)).contains("Fetch Supplier");
    }
}
