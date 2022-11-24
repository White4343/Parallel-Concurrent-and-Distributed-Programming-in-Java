package edu.coursera.distributed;

import edu.coursera.distributed.util.MPI;
import edu.coursera.distributed.util.MPI.MPIException;

/**
 * A wrapper class for a parallel, MPI-based matrix multiply implementation.
 */
public class MatrixMult {
    /**
     * A parallel implementation of matrix multiply using MPI to express SPMD
     * parallelism. In particular, this method should store the output of
     * multiplying the matrices a and b into the matrix c.
     * <p>
     * This method is called simultaneously by all MPI ranks in a running MPI
     * program. For simplicity MPI_Init has already been called, and
     * MPI_Finalize should not be called in parallelMatrixMultiply.
     * <p>
     * On entry to parallelMatrixMultiply, the following will be true of a, b,
     * and c:
     * <p>
     * 1) The matrix a will only be filled with the input values on MPI rank
     * zero. Matrix a on all other ranks will be empty (initialized to all
     * zeros).
     * 2) Likewise, the matrix b will only be filled with input values on MPI
     * rank zero. Matrix b on all other ranks will be empty (initialized to
     * all zeros).
     * 3) Matrix c will be initialized to all zeros on all ranks.
     * <p>
     * Upon returning from parallelMatrixMultiply, the following must be true:
     * <p>
     * 1) On rank zero, matrix c must be filled with the final output of the
     * full matrix multiplication. The contents of matrix c on all other
     * ranks are ignored.
     * <p>
     * Therefore, it is the responsibility of this method to distribute the
     * input data in a and b across all MPI ranks for maximal parallelism,
     * perform the matrix multiply in parallel, and finally collect the output
     * data in c from all ranks back to the zeroth rank. You may use any of the
     * MPI APIs provided in the mpi object to accomplish this.
     * <p>
     * A reference sequential implementation is provided below, demonstrating
     * the use of the Matrix class's APIs.
     *
     * @param a   Input matrix
     * @param b   Input matrix
     * @param c   Output matrix
     * @param mpi MPI object supporting MPI APIs
     * @throws MPIException On MPI error. It is not expected that your
     *                      implementation should throw any MPI errors during
     *                      normal operation.
     */
    public static void parallelMatrixMultiply(Matrix a, Matrix b, Matrix c,
                                              final MPI mpi) throws MPIException {


        /**
         *  SPMD -> Одна и та же программа, работающая на всех процессах/рангах
         * MPI_COMM_WORLD — коммуникатор
         * Матрица реализована в виде одномерного массива, так как MPI использует одномерный буфер для отправки/получения
         **/

        final int myRank = mpi.MPI_Comm_rank(mpi.MPI_COMM_WORLD); // Каждый процесс имеет ранг для его идентификации

        final int size = mpi.MPI_Comm_size(mpi.MPI_COMM_WORLD); // Количество процессов

        final int rows = c.getNRows();
        final int rowChunk = (rows + size - 1) / size; // Деление на куски для каждого процесса. Каждый процесс выполняет только фрагмент строки

        final int start = myRank * rowChunk; // Получаем начальный и конечный индекс каждого фрагмента
        int end = (myRank + 1) * rowChunk;
        if (end > rows) end = rows;

        mpi.MPI_Bcast(a.getValues(), 0, a.getNRows() * a.getNCols(), 0, mpi.MPI_COMM_WORLD); // Отправляет данные в массив => необходимо преобразовать 2D-матрицу в массив
        mpi.MPI_Bcast(b.getValues(), 0, b.getNRows() * b.getNCols(), 0, mpi.MPI_COMM_WORLD);


        for (int i = start; i < end; i++) {
            for (int j = 0; j < c.getNCols(); j++) {
                c.set(i, j, 0.0);
                for (int k = 0; k < b.getNRows(); k++) {
                    c.incr(i, j, a.get(i, k) * b.get(k, j));
                }
            }
        }

        if (myRank == 0) { // Собираем результаты если проверка срабатываем, отправляем если не проходит

            MPI.MPI_Request[] requests = new MPI.MPI_Request[size - 1]; // Буфер для результатов другого процесса, которые получит ранг 0

            for (int i = 1; i < size; i++) { // Перебираем ранги

                final int rankStartRow = i * rowChunk;
                int rankEndRow = (i + 1) * rowChunk;

                if (rankEndRow > rows)
                    rankEndRow = rows; // Если RankEndrow -> (i + 1) * chunkSize больше чем реальное количество сторк
                final int rowOffset = rankStartRow * c.getNCols();

                final int nElements = (rankEndRow - rankStartRow) * c.getNCols(); // Количество элементов в чанке

                // Неблокирующий прием от других рангов от точки к точке (Non blocking point to point)
                requests[i - 1] = mpi.MPI_Irecv(c.getValues(), rowOffset, nElements, i, i, mpi.MPI_COMM_WORLD);
            }
            mpi.MPI_Waitall(requests);
            // подобно async await ждем все другие процессы
        } else {
            // Другие ранги отправляют свои результаты
            mpi.MPI_Send(c.getValues(), start * c.getNCols(), (end - start) * c.getNCols(), 0, myRank, mpi.MPI_COMM_WORLD);
        }
    }
}