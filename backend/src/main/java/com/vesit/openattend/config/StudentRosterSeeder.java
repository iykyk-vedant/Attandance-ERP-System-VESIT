package com.vesit.openattend.config;

import com.vesit.openattend.entity.Student;
import com.vesit.openattend.entity.User;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.repository.StudentRepository;
import com.vesit.openattend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudentRosterSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public record StudentSeed(String rollNo, String name, String email, String division, String batch) {}

    private static final List<StudentSeed> ROSTER = List.of(
            new StudentSeed("D12B-01", "AHUJA MANAV NAVIN JAYA", "2024.manav.ahuja@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-02", "AMARNANI KASHISH NARESH DIVYA", "2024.kashish.amarnani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-03", "ARORA JAPLEEN KAUR PARVINDER SINGH", "2024.japleen.arora@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-04", "AWASTHI SHRUTI RAJEEV SHRADDHA", "2024.shruti.awasthi@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-05", "BHANGALE UNMESH NIRANJAN KAVITA", "2024.unmesh.bhangale@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-06", "CHHATLANI SIA GHANSHYAM AANCHAL", "2024.sia.chhatlani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-07", "DEVADIGA KIRTAN SURESH NAYANA", "2024.kirtan.devadiga@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-08", "DUBEY ANUSHKA SURENDRA POONAM", "2024.anushka.dubey@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-09", "FATNANI BHOOMIT RAJESH SAKSHI", "2024.bhoomit.fatnani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-10", "GALRANI HITIKA MANISH SNEHA", "2024.hitika.galrani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-11", "GAVALI KRISHNA BAPU RANJANA", "2024.krishna.gavali@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-12", "GHODVINDE ADHYATMIKA RAMDAS RASIKA", "d2025.adhyatmika.ghodvinde@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-13", "GORE ABHISHEK ARUN ARCHANA", "2024.abhishek.gore@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-14", "GUPTA KRRISH JITENDRA ANGIRA", "2024.krrish.gupta@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-15", "GUPTA RUSHABH VINOD MEENA", "2024.rushabh.gupta@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-16", "HASWANI DHRUV RAVI SAKSHI", "2024.dhruv.haswani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-17", "HOTWANI SAAKHI VINOD HEENA", "2024.saakhi.hotwani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-18", "KADAM ARYAN VINAY SWAPNA", "2024.aryan.kadam@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-19", "KALE ARNAV MANOJ SMITA", "d2025.arnav.kale@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-20", "KHISMATRAO PARTH RAHUL SWARDA", "2024.parth.khismatrao@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-21", "LACHHWANI SHUBHAM MAHESH BHOOMI", "2024.shubham.lachhwani@ves.ac.in", "D12B", "B1"),
            new StudentSeed("D12B-22", "LALWANI DISHA RAKESH BHAVIKA", "2024.disha.lalwani@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-23", "MADHYAN PARI RAJESH BHAWANA", "2024.pari.madhyan@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-24", "MAHAJAN PRASAD NARENDRA NILIMA", "2024.prasad.mahajan@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-25", "MAINANI LOKESH DHANRAJ KOMAL", "2024.lokesh.mainani@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-26", "MANKAR SHRAVANI JITENDRA LEENA", "2024.shravani.mankar@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-27", "MASCARENHAS AADIT RAJESH RASHMI", "2024.aadit.mascarenhas@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-28", "MHATRE NUPUR LALIT SHRUTI", "2024.nupur.mhatre@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-29", "MOHITE PARTH PARITOSH ARCHANA", "2024.parth.mohite@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-30", "MOTWANI VINAY SUNDER ARTI", "2024.vinay.motwani@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-31", "NAIK GOURISH GANPATI VANITA", "2024.gourish.naik@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-32", "PAHUJA VANSH GOPAL ANISHA", "d2025.vansh.pahuja@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-33", "PAREKAR MANAS SHAILESH SHILPA", "d2025.manas.parekar@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-34", "PATIL ANIRUDDHA SURESH ROHINI", "2024.aniruddha.patil@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-35", "PATIL KAUSTUBH UMESH MANISHA", "2024.kaustubh.patil@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-36", "PATIL PRADNYESH PRASHANT SHEETAL", "2024.pradnyesh.patil@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-37", "PATIL TANAY SAMIR SAMIRA", "2024.tanay.patil@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-38", "PRAKASH SURAJ SANJIV REENA", "2024.prakash.suraj@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-39", "RAGHANI SHANTANU SUNIL KAJAL", "2024.shantanu.raghani@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-40", "RAI AASTHA ASHISH MAMTA", "2024.aastha.rai@ves.ac.in", "D12B", "B2"),
            new StudentSeed("D12B-41", "RAI YASH SUBBAYYA VIJAYALAKSHMI", "2024.yash.rai@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-42", "RAJPAL PRIYANKA JAGDISH DEEPA", "2024.priyanka.rajpal@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-43", "SACHDEV HANSIKA SANDEEP LABHDI", "2024.hansika.sachdev@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-44", "SAWLANI OM GHANSHYAM RADHA", "2024.om.sawlani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-45", "TAWATE TANISHKA AJIT RUPALI", "2024.tanishka.tawate@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-46", "ARUJA ARYA HARISH GEETA", "2024.arya.aruja@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-47", "CHURYAI BHAVESH LADHARAM MADHU", "2024.bhavesh.churyai@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-48", "GAWAD KSHITIJ VASANT SUJATA", "2024.kshitij.gawad@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-49", "GHARAT VEDANT VIKAS VIKRANTI", "2024.vedant.gharat@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-50", "JETHANI MANAV GHANSHYAM ROSHINI", "2024.manav.jethani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-51", "KHALSA JASKARAN JASPAL SINGH", "2024.jaskaran.khalsa@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-52", "KHUBCHANDANI KANCHAN GOVIND KASHISH", "2024.kanchan.khubchandani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-53", "KUMAR RISHABH VINOD LALITA", "2024.rishabh.kumar@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-54", "LOKHANDE AYUSH MADHUKAR KANCHAN", "2024.ayush.lokhande@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-55", "LULLA PRIYAM MAHESH DEEPA", "2024.priyam.lulla@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-56", "MANDHAN RONIT YOGESH MAHEK", "2024.ronit.mandhan@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-57", "NEBHANI GEETIKA NARAIN SNEHA", "2024.geetika.nebhani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-58", "NISHAD DEEPAK RAMNARAYAN RAJKALI", "2024.deepak.nishad@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-59", "PAWAR SHRUTIKA GANESH LEENA", "2024.shrutika.pawar@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-60", "RUPLANI HIMANI KAMLESH PREETI", "2024.himani.ruplani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-61", "SABHANI HISHITA PARSHOTAM KIRAN", "2024.hishita.sabhani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-62", "SANTANI VARUN VINOD DISHA", "2024.varun.santani@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-63", "TALREJA TANISH SANJAY KARISHMA", "2024.tanish.talreja@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-64", "VALECHA HEENA NEERAJ DEEPTI", "2024.heena.valecha@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-65", "VALECHA SAHIL KISHORE RIYA", "2024.sahil.valech@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-66", "VALECHA VANSH MANOJ RITU", "2024.vansh.valecha@ves.ac.in", "D12B", "B3"),
            new StudentSeed("D12B-67", "YADAV AYUSH MUKESH SUNITA", "2024.ayush.yadav@ves.ac.in", "D12B", "B3")
    );

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Bootstrapping VESIT Student Accounts (Password = Email)...");

        for (StudentSeed seed : ROSTER) {
            String email = seed.email().trim().toLowerCase();
            if (!userRepository.existsByEmail(email)) {
                User user = userRepository.save(User.builder()
                        .id(UUID.randomUUID().toString())
                        .email(email)
                        .passwordHash(passwordEncoder.encode(email)) // Password is their email address
                        .role(Role.STUDENT)
                        .isActive(true)
                        .build());

                studentRepository.save(Student.builder()
                        .id(UUID.randomUUID().toString())
                        .user(user)
                        .rollNo(seed.rollNo())
                        .name(seed.name())
                        .division(seed.division())
                        .batch(seed.batch())
                        .build());
            }
        }

        log.info("Successfully provisioned {} VESIT Student accounts.", ROSTER.size());
    }
}
